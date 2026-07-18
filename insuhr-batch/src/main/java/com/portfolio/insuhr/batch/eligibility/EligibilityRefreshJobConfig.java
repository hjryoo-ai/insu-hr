package com.portfolio.insuhr.batch.eligibility;

import com.portfolio.insuhr.batch.support.BatchTargetDate;
import com.portfolio.insuhr.batch.support.LoggingSkipListener;
import com.portfolio.insuhr.domain.eligibility.AgentEligibilityReconciler;
import java.time.Clock;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * {@code eligibilityRefreshJob} — 쓰기 없이 날짜 경계가 넘어가는 자격 전이의 유일한 포착자 (설계서 8 v2.0).
 *
 * <p>실시간 reconcile(Phase 5)이 이미 자격·교육·보증·제재의 모든 <b>쓰기</b>를 정합화하므로, 배치가 잡을 것은 트리거하는 쓰기 없이 날짜만 넘어가는
 * 사건(보수교육 이수기한 도과, 재정보증 만료)뿐이다. 그래서 이 잡은 판정·전이·발행을 <b>재구현하지 않고</b> 기존 {@link
 * AgentEligibilityReconciler#reconcileAsOf}를 그대로 호출한다.
 *
 * <ul>
 *   <li>Reader: 대상(ACTIVE/SUSPENDED) 설계사 <b>ID만</b> 스트리밍(JdbcCursorItemReader).
 *   <li>Processor: {@code reconcileAsOf(agentId, targetDate)} 호출. reconcile은
 *       {@code @Transactional(REQUIRED)}이라 청크 트랜잭션에 합류하고 상태 전이 + Outbox가 한 트랜잭션에 묶인다(설계서 10.1.1).
 *   <li>개별 실패는 skip+로그, 잡 전체는 완주(설계서 8).
 * </ul>
 *
 * <p>시나리오 2(보수교육 기한 도과 → SUSPENDED + 이벤트)가 이 잡에서 완결된다.
 */
@Configuration
public class EligibilityRefreshJobConfig {

  private static final int CHUNK = 100;

  @Bean
  public Job eligibilityRefreshJob(JobRepository jobRepository, Step eligibilityRefreshStep) {
    return new JobBuilder("eligibilityRefreshJob", jobRepository)
        .start(eligibilityRefreshStep)
        .build();
  }

  @Bean
  public Step eligibilityRefreshStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      JdbcCursorItemReader<Long> eligibilityAgentReader,
      ItemProcessor<Long, Long> eligibilityReconcileProcessor) {
    return new StepBuilder("eligibilityRefreshStep", jobRepository)
        .<Long, Long>chunk(CHUNK)
        .reader(eligibilityAgentReader)
        .processor(eligibilityReconcileProcessor)
        .writer(noOpWriter()) // 처리(reconcile)는 프로세서가 끝낸다 — 별도 쓰기 없음
        .transactionManager(transactionManager)
        .faultTolerant()
        .skip(Exception.class)
        .skipLimit(10_000)
        .skipListener(new LoggingSkipListener<Long, Long>())
        .build();
  }

  @Bean
  @StepScope
  public JdbcCursorItemReader<Long> eligibilityAgentReader(DataSource dataSource) {
    return new JdbcCursorItemReaderBuilder<Long>()
        .name("eligibilityAgentReader")
        .dataSource(dataSource)
        .sql(
            "SELECT AGENT_ID FROM TB_AGENT WHERE AGENT_STATUS_CD IN ('ACTIVE','SUSPENDED')"
                + " ORDER BY AGENT_ID")
        .rowMapper((rs, rowNum) -> rs.getLong(1))
        .build();
  }

  @Bean
  @StepScope
  public ItemProcessor<Long, Long> eligibilityReconcileProcessor(
      AgentEligibilityReconciler reconciler,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    return agentId -> {
      reconciler.reconcileAsOf(agentId, asOf);
      return agentId;
    };
  }

  private static ItemWriter<Long> noOpWriter() {
    return chunk -> {};
  }
}
