package com.portfolio.insuhr.batch.privacy;

import com.portfolio.insuhr.batch.support.BatchTargetDate;
import com.portfolio.insuhr.batch.support.LoggingSkipListener;
import com.portfolio.insuhr.domain.person.PersonPurgeService;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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

/**
 * {@code privacyPurgeJob} — 개인정보 파기(익명화) (설계서 8, 5.2 v1.4, Phase 8).
 *
 * <p>로스터에서 <b>지우는</b> 유일한 잡이다. 대상은 두 종류(하나의 Reader가 {@code UNION}으로 집는다):
 *
 * <ul>
 *   <li><b>ROLE_ENDED</b> — <b>모든 역할이 종료</b>되고 {@code max(퇴직일, 해촉일) +
 *       보존기간(PRIVACY_RETENTION_YEARS)} 경과. 한 사람이 직원+설계사 이중 역할일 수 있으므로(5.2), <b>둘 다</b> 종료돼야 한다 — 한
 *       역할만 보고 지우면 재직 중인 사람의 주민번호가 날아간다.
 *   <li><b>ORPHAN</b> — 역할이 끝내 생성되지 않은 인물({@code ORPHAN_PERSON_PURGE_DAYS} 경과). Phase 2의
 *       REQUIRES_NEW 결정에서 파생된, 목적 없이 남은 개인정보(5.2 v1.4).
 * </ul>
 *
 * <p><b>재실행 멱등</b>: 익명화된 인물은 {@code RRN_HASH}가 NULL이 되어 대상 술어({@code RRN_HASH IS NOT NULL})에서 빠진다 —
 * 술어 자체가 멱등 방어선이다({@code UQ_PURGE_LEDGER_PERSON}은 백스톱). 집행은 도메인 {@link PersonPurgeService}가 소유한다.
 */
@Configuration
public class PrivacyPurgeJobConfig {

  private static final int CHUNK = 100;

  /** 파기 대상 한 인물. */
  public record PurgeTarget(long personId, String purgeTypeCd, LocalDate basisDate) {}

  @Bean
  public Job privacyPurgeJob(JobRepository jobRepository, Step privacyPurgeStep) {
    return new JobBuilder("privacyPurgeJob", jobRepository).start(privacyPurgeStep).build();
  }

  @Bean
  public Step privacyPurgeStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      IteratorItemReader<PurgeTarget> purgeTargetReader,
      ItemProcessor<PurgeTarget, PurgeTarget> purgeProcessor) {
    return new StepBuilder("privacyPurgeStep", jobRepository)
        .<PurgeTarget, PurgeTarget>chunk(CHUNK)
        .reader(purgeTargetReader)
        .processor(purgeProcessor)
        .writer(noOpWriter())
        .transactionManager(transactionManager)
        .faultTolerant()
        .skip(Exception.class)
        .skipLimit(10_000)
        .skipListener(new LoggingSkipListener<PurgeTarget, PurgeTarget>())
        .build();
  }

  @Bean
  @StepScope
  public IteratorItemReader<PurgeTarget> purgeTargetReader(
      JdbcClient jdbcClient,
      PolicyConfigService policyConfigService,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    LocalDate retentionCutoff =
        asOf.minusYears(policyConfigService.getInt(PolicyKey.PRIVACY_RETENTION_YEARS, asOf));
    LocalDate orphanCutoff =
        asOf.minusDays(policyConfigService.getInt(PolicyKey.ORPHAN_PERSON_PURGE_DAYS, asOf));

    List<PurgeTarget> targets =
        jdbcClient
            .sql(
                """
                SELECT p.PERSON_ID AS PERSON_ID,
                       'ROLE_ENDED' AS PURGE_TYPE,
                       GREATEST(NVL(e.RESIGN_DT, DATE '0001-01-01'),
                                NVL(a.TERMINATE_DT, DATE '0001-01-01')) AS BASIS_DT
                  FROM TB_PERSON p
                  LEFT JOIN TB_EMP e   ON e.PERSON_ID = p.PERSON_ID
                  LEFT JOIN TB_AGENT a ON a.PERSON_ID = p.PERSON_ID
                 WHERE p.RRN_HASH IS NOT NULL
                   AND (e.EMP_ID IS NOT NULL OR a.AGENT_ID IS NOT NULL)
                   AND (e.EMP_ID IS NULL
                        OR (e.EMP_STATUS_CD = 'RESIGNED' AND e.RESIGN_DT IS NOT NULL))
                   AND (a.AGENT_ID IS NULL
                        OR (a.AGENT_STATUS_CD = 'TERMINATED' AND a.TERMINATE_DT IS NOT NULL))
                   AND GREATEST(NVL(e.RESIGN_DT, DATE '0001-01-01'),
                                NVL(a.TERMINATE_DT, DATE '0001-01-01')) <= :retentionCutoff
                UNION ALL
                SELECT p.PERSON_ID AS PERSON_ID,
                       'ORPHAN' AS PURGE_TYPE,
                       CAST(p.CREATED_AT AS DATE) AS BASIS_DT
                  FROM TB_PERSON p
                 WHERE p.RRN_HASH IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM TB_EMP e WHERE e.PERSON_ID = p.PERSON_ID)
                   AND NOT EXISTS (SELECT 1 FROM TB_AGENT a WHERE a.PERSON_ID = p.PERSON_ID)
                   AND CAST(p.CREATED_AT AS DATE) <= :orphanCutoff
                 ORDER BY 1
                """)
            .param("retentionCutoff", retentionCutoff)
            .param("orphanCutoff", orphanCutoff)
            .query(
                (rs, rowNum) ->
                    new PurgeTarget(
                        rs.getLong("PERSON_ID"),
                        rs.getString("PURGE_TYPE"),
                        rs.getDate("BASIS_DT").toLocalDate()))
            .list();
    return new IteratorItemReader<>(targets);
  }

  @Bean
  @StepScope
  public ItemProcessor<PurgeTarget, PurgeTarget> purgeProcessor(
      PersonPurgeService personPurgeService,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    return target -> {
      personPurgeService.purge(target.personId(), target.purgeTypeCd(), target.basisDate(), asOf);
      return target;
    };
  }

  private static ItemWriter<PurgeTarget> noOpWriter() {
    return chunk -> {};
  }
}
