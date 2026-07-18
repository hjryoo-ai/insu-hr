package com.portfolio.insuhr.batch.dq;

import com.portfolio.insuhr.batch.support.BatchTargetDate;
import com.portfolio.insuhr.batch.support.DqSweepTasklet;
import com.portfolio.insuhr.domain.dq.DqFindingDao;
import com.portfolio.insuhr.domain.dq.DqRule;
import java.time.Clock;
import java.util.List;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code outboxDlqSweepJob} — 연계 전달 정체 감시 스위프 (설계서 8 v2.0 재정의).
 *
 * <p><b>재전송이 아니라 관측만</b> 한다(설계서 9.2 v1.8, 8 v2.0). 백오프 재시도는 릴레이가 {@code NEXT_RETRY_AT}로 이미 소유하므로 잡이
 * 다시 쏘면 이중 전송 경합이 난다 — 수동 재전송은 {@code POST /admin/outbox/{id}/resend}가 담당한다. 잡은 한도 소진 {@code
 * FAILED} 전달과 오래 머문 {@code READY}/{@code FANNED_OUT}/{@code PENDING}을 {@code TB_DQ_FINDING}에 적재한다.
 *
 * <p>정체 판정은 잡 규약대로 시스템 시각이 아니라 {@code targetDate}({@code :foundDt})를 기준으로 <b>일 경계</b>로 본다 — "오늘 이전에
 * 발생했는데 아직 종결 안 됨". {@code TB_NOTICE_QUEUE}(행동 촉구 알림)와 달리 이건 <b>운영 발견</b>이라 {@code TB_DQ_FINDING}에
 * 둔다(용도 구분, 설계서 8 v2.0).
 */
@Configuration
public class OutboxDlqSweepJobConfig {

  /** 재시도 한도 소진으로 FAILED 종결된 전달 레코드(수동 재전송 후보). */
  private static final DqRule DELIVERY_FAILED =
      new DqRule(
          "DELIVERY_FAILED",
          "DELIVERY",
          """
          SELECT d.DELIVERY_ID AS TARGET_ID,
                 JSON_OBJECT('eventId' VALUE d.EVENT_ID,
                             'subscriberId' VALUE d.SUBSCRIBER_ID,
                             'retryCnt' VALUE d.RETRY_CNT,
                             'aggType' VALUE d.AGG_TYPE,
                             'aggId' VALUE d.AGG_ID
                             RETURNING VARCHAR2(4000)) AS DETAIL
            FROM TB_IF_DELIVERY d
           WHERE d.STATUS_CD = 'FAILED'
          """);

  /** 오늘 이전에 발생했는데 아직 READY/FANNED_OUT에 머문 Outbox 이벤트(정체). */
  private static final DqRule OUTBOX_STALL =
      new DqRule(
          "OUTBOX_STALL",
          "OUTBOX",
          """
          SELECT o.EVENT_ID AS TARGET_ID,
                 JSON_OBJECT('status' VALUE o.STATUS_CD,
                             'eventType' VALUE o.EVENT_TYPE,
                             'occurredDate' VALUE TO_CHAR(o.OCCURRED_AT, 'YYYY-MM-DD')
                             RETURNING VARCHAR2(4000)) AS DETAIL
            FROM TB_IF_OUTBOX o
           WHERE o.STATUS_CD IN ('READY', 'FANNED_OUT')
             AND CAST(o.OCCURRED_AT AS DATE) < :foundDt
          """);

  /** 오늘 이전에 만들어졌는데 아직 PENDING인 전달 레코드(정체). */
  private static final DqRule DELIVERY_STALL_PENDING =
      new DqRule(
          "DELIVERY_STALL_PENDING",
          "DELIVERY",
          """
          SELECT d.DELIVERY_ID AS TARGET_ID,
                 JSON_OBJECT('eventId' VALUE d.EVENT_ID,
                             'subscriberId' VALUE d.SUBSCRIBER_ID,
                             'status' VALUE d.STATUS_CD,
                             'createdDate' VALUE TO_CHAR(d.CREATED_AT, 'YYYY-MM-DD')
                             RETURNING VARCHAR2(4000)) AS DETAIL
            FROM TB_IF_DELIVERY d
           WHERE d.STATUS_CD = 'PENDING'
             AND CAST(d.CREATED_AT AS DATE) < :foundDt
          """);

  private static final List<DqRule> RULES =
      List.of(DELIVERY_FAILED, OUTBOX_STALL, DELIVERY_STALL_PENDING);

  @Bean
  public Job outboxDlqSweepJob(JobRepository jobRepository, Step outboxDlqSweepStep) {
    return new JobBuilder("outboxDlqSweepJob", jobRepository).start(outboxDlqSweepStep).build();
  }

  @Bean
  public Step outboxDlqSweepStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      Tasklet outboxDlqSweepTasklet) {
    return new StepBuilder("outboxDlqSweepStep", jobRepository)
        .tasklet(outboxDlqSweepTasklet, transactionManager)
        .build();
  }

  @Bean
  @StepScope
  public Tasklet outboxDlqSweepTasklet(
      DqFindingDao dqFindingDao,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    return DqSweepTasklet.of(dqFindingDao, RULES, BatchTargetDate.resolve(stepExecution, clock));
  }
}
