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
 * {@code licenseValidityJob} — 자격·협회 상태와 위촉상태의 정합성 점검 리포트 (설계서 8 v2.0).
 *
 * <p><b>경계 결정</b>(설계서 8 v2.0): 이 잡은 <b>살아있는 설계사의 자격 상태 모순</b>을 본다 — 위촉은 ACTIVE인데 판매자격이 REVOKED이거나
 * 협회등록이 DEREGISTERED인 경우. 판매자격·협회는 날짜가 아니라 상태로만 유효성이 정해지므로(설계서 6.4) 순수 상태 대조다. 조직 구조의 결손(폐지 조직 소속,
 * 협회등록 전무)은 {@code dataQualityJob}이 본다 — 둘은 대상 도메인이 달라 별도 잡으로 둔다.
 *
 * <p>발견은 {@code TB_DQ_FINDING}에 {@code UQ(RULE_CD, TARGET_ID, FOUND_DT)}로 멱등 적재된다 — 같은 {@code
 * targetDate} 재실행은 중복을 안 만든다. 상태를 바꾸지 않으므로 자동 전이·이벤트는 없다(그건 reconcile의 몫).
 */
@Configuration
public class LicenseValidityJobConfig {

  /** ACTIVE 설계사가 REVOKED 판매자격을 들고 있음 — 자격 말소와 위촉 유지의 모순. */
  private static final DqRule LICENSE_REVOKED_ACTIVE =
      new DqRule(
          "LICENSE_REVOKED_ACTIVE",
          "AGENT",
          """
          SELECT a.AGENT_ID AS TARGET_ID,
                 JSON_OBJECT('agentCd' VALUE MAX(a.AGENT_CD),
                             'revokedCount' VALUE COUNT(*)
                             RETURNING VARCHAR2(4000)) AS DETAIL
            FROM TB_AGENT a
            JOIN TB_AGENT_LICENSE l ON l.AGENT_ID = a.AGENT_ID
           WHERE a.AGENT_STATUS_CD = 'ACTIVE'
             AND l.STATUS_CD = 'REVOKED'
           GROUP BY a.AGENT_ID
          """);

  /** ACTIVE 설계사가 DEREGISTERED 협회등록을 들고 있음 — 협회 말소와 위촉 유지의 모순. */
  private static final DqRule ASSOC_DEREG_ACTIVE =
      new DqRule(
          "ASSOC_DEREG_ACTIVE",
          "AGENT",
          """
          SELECT a.AGENT_ID AS TARGET_ID,
                 JSON_OBJECT('agentCd' VALUE MAX(a.AGENT_CD),
                             'deregCount' VALUE COUNT(*)
                             RETURNING VARCHAR2(4000)) AS DETAIL
            FROM TB_AGENT a
            JOIN TB_ASSOC_REG r ON r.AGENT_ID = a.AGENT_ID
           WHERE a.AGENT_STATUS_CD = 'ACTIVE'
             AND r.STATUS_CD = 'DEREGISTERED'
           GROUP BY a.AGENT_ID
          """);

  private static final List<DqRule> RULES = List.of(LICENSE_REVOKED_ACTIVE, ASSOC_DEREG_ACTIVE);

  @Bean
  public Job licenseValidityJob(JobRepository jobRepository, Step licenseValidityStep) {
    return new JobBuilder("licenseValidityJob", jobRepository).start(licenseValidityStep).build();
  }

  @Bean
  public Step licenseValidityStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      Tasklet licenseValidityTasklet) {
    return new StepBuilder("licenseValidityStep", jobRepository)
        .tasklet(licenseValidityTasklet, transactionManager)
        .build();
  }

  @Bean
  @StepScope
  public Tasklet licenseValidityTasklet(
      DqFindingDao dqFindingDao,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    return DqSweepTasklet.of(dqFindingDao, RULES, BatchTargetDate.resolve(stepExecution, clock));
  }
}
