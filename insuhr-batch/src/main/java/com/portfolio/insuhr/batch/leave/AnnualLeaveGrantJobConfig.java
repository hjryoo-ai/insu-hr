package com.portfolio.insuhr.batch.leave;

import com.portfolio.insuhr.batch.support.BatchTargetDate;
import com.portfolio.insuhr.batch.support.LoggingSkipListener;
import com.portfolio.insuhr.domain.leave.AnnualLeaveGrantService;
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
 * {@code annualLeaveGrantJob} — 회계연도 연차 일괄 부여 (설계서 8 v2.0).
 *
 * <p><b>부여 모델은 회계연도 일괄로 확정</b>(설계서 8 v2.0) — 입사기념일 개별 기산이 아니다. {@code targetDate}가 든 해의 연차를 재직/휴직
 * 임직원에게 부여한다. 부여 규칙(근속 기준 일수)은 배치가 아니라 도메인 {@link AnnualLeaveGrantService}가 소유한다.
 *
 * <p>Reader가 <b>그 해 아직 부여받지 않은</b> 임직원만 집으므로({@code NOT EXISTS}) 재실행은 대상이 비어 자연 no-op이고, 도메인 서비스의
 * 존재 검사와 스키마 {@code UQ_LEAVE_GRANT(EMP_ID, YEAR_NO)}가 이중 방어선이다(설계서 V10).
 */
@Configuration
public class AnnualLeaveGrantJobConfig {

  private static final int CHUNK = 200;

  @Bean
  public Job annualLeaveGrantJob(JobRepository jobRepository, Step annualLeaveGrantStep) {
    return new JobBuilder("annualLeaveGrantJob", jobRepository).start(annualLeaveGrantStep).build();
  }

  @Bean
  public Step annualLeaveGrantStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      IteratorItemReader<Long> ungrantedEmpReader,
      ItemProcessor<Long, Long> annualLeaveGrantProcessor) {
    return new StepBuilder("annualLeaveGrantStep", jobRepository)
        .<Long, Long>chunk(CHUNK)
        .reader(ungrantedEmpReader)
        .processor(annualLeaveGrantProcessor)
        .writer(noOpWriter())
        .transactionManager(transactionManager)
        .faultTolerant()
        .skip(Exception.class)
        .skipLimit(10_000)
        .skipListener(new LoggingSkipListener<Long, Long>())
        .build();
  }

  @Bean
  @StepScope
  public IteratorItemReader<Long> ungrantedEmpReader(
      JdbcClient jdbcClient, Clock clock, @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    int yearNo = asOf.getYear();
    List<Long> ids =
        jdbcClient
            .sql(
                """
                SELECT e.EMP_ID
                  FROM TB_EMP e
                 WHERE e.EMP_STATUS_CD IN ('ACTIVE', 'ON_LEAVE')
                   AND NOT EXISTS (SELECT 1
                                     FROM TB_LEAVE_GRANT g
                                    WHERE g.EMP_ID = e.EMP_ID
                                      AND g.YEAR_NO = :yearNo)
                 ORDER BY e.EMP_ID
                """)
            .param("yearNo", yearNo)
            .query(Long.class)
            .list();
    return new IteratorItemReader<>(ids);
  }

  @Bean
  @StepScope
  public ItemProcessor<Long, Long> annualLeaveGrantProcessor(
      AnnualLeaveGrantService annualLeaveGrantService,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    return empId -> {
      annualLeaveGrantService.grantFor(empId, asOf); // 멱등: 이미 부여됐으면 no-op
      return empId;
    };
  }

  private static ItemWriter<Long> noOpWriter() {
    return chunk -> {};
  }
}
