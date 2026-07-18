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
 * {@code dataQualityJob} — 조직 구조 정합성 점검 리포트 (설계서 8 v2.0, Phase 8→Phase 7 후속 편입).
 *
 * <p><b>경계 결정</b>(설계서 8 v2.0): 이 잡은 <b>구조적 결손</b>을 본다 — 폐지 조직({@code USE_YN='N'})에 소속된 재직자, 협회등록이
 * 하나도 없는 ACTIVE 설계사. 자격/협회의 상태 모순은 {@code licenseValidityJob}이 본다. {@link
 * com.portfolio.insuhr.batch.support.DqSweepTasklet 공통 골격}을 공유하되 룰셋만 다르다.
 */
@Configuration
public class DataQualityJobConfig {

  /** 폐지된 조직(USE_YN='N')에 소속된 재직/휴직 임직원. */
  private static final DqRule EMP_ORG_ABOLISHED =
      new DqRule(
          "EMP_ORG_ABOLISHED",
          "EMP",
          """
          SELECT e.EMP_ID AS TARGET_ID,
                 JSON_OBJECT('empNo' VALUE e.EMP_NO, 'orgId' VALUE e.ORG_ID
                             RETURNING VARCHAR2(4000)) AS DETAIL
            FROM TB_EMP e
            JOIN TB_ORG o ON o.ORG_ID = e.ORG_ID
           WHERE e.EMP_STATUS_CD IN ('ACTIVE', 'ON_LEAVE')
             AND o.USE_YN = 'N'
          """);

  /** ACTIVE인데 등록(REGISTERED) 협회가 하나도 없는 설계사. */
  private static final DqRule AGENT_ACTIVE_NO_ASSOC =
      new DqRule(
          "AGENT_ACTIVE_NO_ASSOC",
          "AGENT",
          """
          SELECT a.AGENT_ID AS TARGET_ID,
                 JSON_OBJECT('agentCd' VALUE a.AGENT_CD
                             RETURNING VARCHAR2(4000)) AS DETAIL
            FROM TB_AGENT a
           WHERE a.AGENT_STATUS_CD = 'ACTIVE'
             AND NOT EXISTS (SELECT 1
                               FROM TB_ASSOC_REG r
                              WHERE r.AGENT_ID = a.AGENT_ID
                                AND r.STATUS_CD = 'REGISTERED')
          """);

  private static final List<DqRule> RULES = List.of(EMP_ORG_ABOLISHED, AGENT_ACTIVE_NO_ASSOC);

  @Bean
  public Job dataQualityJob(JobRepository jobRepository, Step dataQualityStep) {
    return new JobBuilder("dataQualityJob", jobRepository).start(dataQualityStep).build();
  }

  @Bean
  public Step dataQualityStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      Tasklet dataQualityTasklet) {
    return new StepBuilder("dataQualityStep", jobRepository)
        .tasklet(dataQualityTasklet, transactionManager)
        .build();
  }

  @Bean
  @StepScope
  public Tasklet dataQualityTasklet(
      DqFindingDao dqFindingDao,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    return DqSweepTasklet.of(dqFindingDao, RULES, BatchTargetDate.resolve(stepExecution, clock));
  }
}
