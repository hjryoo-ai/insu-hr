package com.portfolio.insuhr.batch.notice;

import com.portfolio.insuhr.batch.support.BatchTargetDate;
import com.portfolio.insuhr.batch.support.LoggingSkipListener;
import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.integration.IntegrationRecorder;
import com.portfolio.insuhr.domain.notice.NoticeQueueDao;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.IteratorItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code continuingEduNoticeJob} — 보수교육 이수기한 D-60/D-30/D-7 알림 적재 (설계서 8 v2.0, 시나리오 없음/후속).
 *
 * <p>대상 = ACTIVE/SUSPENDED 설계사의 보수교육 이수기한(최신 CONTINUING {@code NEXT_DUE_DT}, 이력 없으면 기저선 {@code
 * LAST_APPOINT_DT + 주기} — 온라인 {@code RecruitEligibilityService}와 같은 기저선)이 {@code targetDate}~+60일에
 * 든 것. 프로세서가 마일스톤별로 {@link NoticeQueueDao#enqueue}를 호출한다.
 *
 * <p><b>마일스톤 술어는 등식이 아니라 부등식</b>(설계서 8 v2.0): {@code DUE_DT <= targetDate + M}이라서 잡이 며칠 죽었다 복구돼도 놓친
 * 마일스톤이 <b>그날 늦게라도</b> 나간다(등식이면 영영 안 나감). 중복은 {@code UQ_NOTICE_QUEUE}가 막는다 — 정상 운영에선 D-M이 되는 첫날 한
 * 번만, 다운타임 뒤엔 늦게 한 번만.
 *
 * <p><b>조건부 발행</b>: {@code enqueue}가 실제로 행을 만든 경우(반환 1)에만 {@code notice.created}를 발행한다 — 재실행은 0을 돌려
 * no-op. 페이로드는 마스킹·업무키만(설계서 9.3).
 */
@Configuration
public class ContinuingEduNoticeJobConfig {

  private static final int CHUNK = 100;
  private static final int WINDOW_DAYS = 60; // 가장 넓은 마일스톤
  private static final int[] MILESTONES = {60, 30, 7};
  private static final String NOTICE_TYPE = "CONT_EDU_DUE";

  /** 보수교육 이수기한이 도래하는 설계사 한 행. */
  public record ContinuingEduDue(long agentId, String agentCd, LocalDate dueDt) {}

  @Bean
  public Job continuingEduNoticeJob(JobRepository jobRepository, Step continuingEduNoticeStep) {
    return new JobBuilder("continuingEduNoticeJob", jobRepository)
        .start(continuingEduNoticeStep)
        .build();
  }

  @Bean
  public Step continuingEduNoticeStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      IteratorItemReader<ContinuingEduDue> continuingEduDueReader,
      ItemProcessor<ContinuingEduDue, ContinuingEduDue> continuingEduNoticeProcessor) {
    return new StepBuilder("continuingEduNoticeStep", jobRepository)
        .<ContinuingEduDue, ContinuingEduDue>chunk(CHUNK)
        .reader(continuingEduDueReader)
        .processor(continuingEduNoticeProcessor)
        .writer(noOpWriter())
        .transactionManager(transactionManager)
        .faultTolerant()
        .skip(Exception.class)
        .skipLimit(10_000)
        .skipListener(new LoggingSkipListener<ContinuingEduDue, ContinuingEduDue>())
        .build();
  }

  @Bean
  @StepScope
  public IteratorItemReader<ContinuingEduDue> continuingEduDueReader(
      JdbcClient jdbcClient,
      PolicyConfigService policyConfigService,
      java.time.Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    int cycle = policyConfigService.getInt(PolicyKey.CONT_EDU_CYCLE_MONTHS, asOf);
    LocalDate asOfMax = asOf.plusDays(WINDOW_DAYS);
    List<ContinuingEduDue> due =
        jdbcClient
            .sql(
                """
                SELECT a.AGENT_ID AS AGENT_ID,
                       a.AGENT_CD AS AGENT_CD,
                       COALESCE(ce.MAX_DUE, ADD_MONTHS(a.LAST_APPOINT_DT, :cycle)) AS DUE_DT
                  FROM TB_AGENT a
                  LEFT JOIN (SELECT AGENT_ID, MAX(NEXT_DUE_DT) AS MAX_DUE
                               FROM TB_AGENT_EDU
                              WHERE EDU_TYPE_CD = 'CONTINUING'
                              GROUP BY AGENT_ID) ce ON ce.AGENT_ID = a.AGENT_ID
                 WHERE a.AGENT_STATUS_CD IN ('ACTIVE', 'SUSPENDED')
                   AND a.LAST_APPOINT_DT IS NOT NULL
                   AND COALESCE(ce.MAX_DUE, ADD_MONTHS(a.LAST_APPOINT_DT, :cycle))
                       BETWEEN :asOf AND :asOfMax
                 ORDER BY a.AGENT_ID
                """)
            .param("cycle", cycle)
            .param("asOf", asOf)
            .param("asOfMax", asOfMax)
            .query(
                (rs, rowNum) ->
                    new ContinuingEduDue(
                        rs.getLong("AGENT_ID"),
                        rs.getString("AGENT_CD"),
                        rs.getDate("DUE_DT").toLocalDate()))
            .list();
    return new IteratorItemReader<>(due);
  }

  @Bean
  @StepScope
  public ItemProcessor<ContinuingEduDue, ContinuingEduDue> continuingEduNoticeProcessor(
      NoticeQueueDao noticeQueueDao,
      IntegrationRecorder integrationRecorder,
      ObjectMapper objectMapper,
      java.time.Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    return due -> {
      for (int m : MILESTONES) {
        if (due.dueDt().isAfter(asOf.plusDays(m))) {
          continue; // 아직 이 마일스톤 창에 안 들어옴 (DUE_DT > targetDate + M)
        }
        String milestone = "D" + m;
        Map<String, Object> payload = noticePayload(due, milestone);
        int inserted =
            noticeQueueDao.enqueue(
                NOTICE_TYPE,
                "AGENT",
                due.agentId(),
                due.dueDt(),
                milestone,
                objectMapper.writeValueAsString(payload));
        if (inserted == 1) {
          integrationRecorder.record(
              IntegrationEvent.created(
                  "notice.created", "AGENT", due.agentId(), due.agentCd(), payload));
        }
      }
      return due;
    };
  }

  private static Map<String, Object> noticePayload(ContinuingEduDue due, String milestone) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("noticeType", NOTICE_TYPE);
    payload.put("targetType", "AGENT");
    payload.put("agentCd", due.agentCd()); // 업무키만 — 마스킹 이름/민감정보 없음
    payload.put("dueDt", due.dueDt().toString());
    payload.put("milestone", milestone);
    return payload;
  }

  private static ItemWriter<ContinuingEduDue> noOpWriter() {
    return chunk -> {};
  }
}
