package com.portfolio.insuhr.batch.guarantee;

import com.portfolio.insuhr.batch.support.BatchTargetDate;
import com.portfolio.insuhr.batch.support.LoggingSkipListener;
import com.portfolio.insuhr.domain.agent.GuaranteeExpiryService;
import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.integration.IntegrationRecorder;
import com.portfolio.insuhr.domain.notice.NoticeQueueDao;
import java.time.Clock;
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
 * {@code guaranteeExpiryJob} — 재정보증 D-30 알림 + D-0 EXPIRED 물질화 (설계서 8 v2.0).
 *
 * <p><b>로스터에서 상태를 직접 바꾸는 유일한 잡</b>이다. 두 스텝:
 *
 * <ul>
 *   <li>① {@code guaranteeNoticeStep} — 만료 30일 이내 ACTIVE 보증에 D-30 알림 적재({@link NoticeQueueDao}, 조건부
 *       {@code notice.created}). continuingEdu와 같은 부등식+dedup.
 *   <li>② {@code guaranteeExpireStep} — {@code END_DT < targetDate}인 ACTIVE 보증을 {@link
 *       GuaranteeExpiryService#expireGuarantee}로 EXPIRED 물질화. 그 도메인 서비스가 <b>온라인과 같은 규약</b>으로
 *       reconcile을 유발하므로 "만료 처리 시 eligibility 재판정 트리거"가 별도 코드 없이 성립한다.
 * </ul>
 *
 * <p>물질화 경계({@code END_DT < targetDate})는 판정 경계({@code END_DT >= asOf}면 유효)와 정확히 대칭이라 {@code
 * eligibilityRefreshJob}과 순서 무관이다(설계서 8 v2.0).
 */
@Configuration
public class GuaranteeExpiryJobConfig {

  private static final int CHUNK = 100;
  private static final int NOTICE_WINDOW_DAYS = 30;
  private static final String MILESTONE = "D30";
  private static final String NOTICE_TYPE = "GUARANTEE_EXPIRY";

  /** 만료가 다가오는 재정보증 한 행. */
  public record GuaranteeExpiryDue(long grntId, long agentId, String agentCd, LocalDate endDt) {}

  @Bean
  public Job guaranteeExpiryJob(
      JobRepository jobRepository, Step guaranteeNoticeStep, Step guaranteeExpireStep) {
    return new JobBuilder("guaranteeExpiryJob", jobRepository)
        .start(guaranteeNoticeStep)
        .next(guaranteeExpireStep)
        .build();
  }

  // ──────────────────────────────── ① D-30 알림 스텝 ────────────────────────────────

  @Bean
  public Step guaranteeNoticeStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      IteratorItemReader<GuaranteeExpiryDue> guaranteeDueReader,
      ItemProcessor<GuaranteeExpiryDue, GuaranteeExpiryDue> guaranteeNoticeProcessor) {
    return new StepBuilder("guaranteeNoticeStep", jobRepository)
        .<GuaranteeExpiryDue, GuaranteeExpiryDue>chunk(CHUNK)
        .reader(guaranteeDueReader)
        .processor(guaranteeNoticeProcessor)
        .writer(noOpWriter())
        .transactionManager(transactionManager)
        .faultTolerant()
        .skip(Exception.class)
        .skipLimit(10_000)
        .skipListener(new LoggingSkipListener<GuaranteeExpiryDue, GuaranteeExpiryDue>())
        .build();
  }

  @Bean
  @StepScope
  public IteratorItemReader<GuaranteeExpiryDue> guaranteeDueReader(
      JdbcClient jdbcClient, Clock clock, @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    LocalDate asOfMax = asOf.plusDays(NOTICE_WINDOW_DAYS);
    List<GuaranteeExpiryDue> due =
        jdbcClient
            .sql(
                """
                SELECT g.GRNT_ID AS GRNT_ID,
                       g.AGENT_ID AS AGENT_ID,
                       a.AGENT_CD AS AGENT_CD,
                       g.END_DT   AS END_DT
                  FROM TB_FIN_GUARANTEE g
                  JOIN TB_AGENT a ON a.AGENT_ID = g.AGENT_ID
                 WHERE g.STATUS_CD = 'ACTIVE'
                   AND g.END_DT BETWEEN :asOf AND :asOfMax
                 ORDER BY g.GRNT_ID
                """)
            .param("asOf", asOf)
            .param("asOfMax", asOfMax)
            .query(
                (rs, rowNum) ->
                    new GuaranteeExpiryDue(
                        rs.getLong("GRNT_ID"),
                        rs.getLong("AGENT_ID"),
                        rs.getString("AGENT_CD"),
                        rs.getDate("END_DT").toLocalDate()))
            .list();
    return new IteratorItemReader<>(due);
  }

  @Bean
  @StepScope
  public ItemProcessor<GuaranteeExpiryDue, GuaranteeExpiryDue> guaranteeNoticeProcessor(
      NoticeQueueDao noticeQueueDao,
      IntegrationRecorder integrationRecorder,
      ObjectMapper objectMapper) {
    return due -> {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("noticeType", NOTICE_TYPE);
      payload.put("targetType", "GUARANTEE");
      payload.put("grntId", due.grntId());
      payload.put("agentCd", due.agentCd()); // 업무키만
      payload.put("dueDt", due.endDt().toString());
      payload.put("milestone", MILESTONE);
      int inserted =
          noticeQueueDao.enqueue(
              NOTICE_TYPE,
              "GUARANTEE",
              due.grntId(),
              due.endDt(),
              MILESTONE,
              objectMapper.writeValueAsString(payload));
      if (inserted == 1) {
        integrationRecorder.record(
            IntegrationEvent.created(
                "notice.created", "AGENT", due.agentId(), due.agentCd(), payload));
      }
      return due;
    };
  }

  // ──────────────────────────────── ② D-0 EXPIRED 물질화 스텝 ────────────────────────────────

  @Bean
  public Step guaranteeExpireStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      IteratorItemReader<Long> expiredGuaranteeReader,
      ItemProcessor<Long, Long> guaranteeExpireProcessor) {
    return new StepBuilder("guaranteeExpireStep", jobRepository)
        .<Long, Long>chunk(CHUNK)
        .reader(expiredGuaranteeReader)
        .processor(guaranteeExpireProcessor)
        .writer(noOpLongWriter())
        .transactionManager(transactionManager)
        .faultTolerant()
        .skip(Exception.class)
        .skipLimit(10_000)
        .skipListener(new LoggingSkipListener<Long, Long>())
        .build();
  }

  @Bean
  @StepScope
  public IteratorItemReader<Long> expiredGuaranteeReader(
      JdbcClient jdbcClient, Clock clock, @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    List<Long> ids =
        jdbcClient
            .sql(
                "SELECT GRNT_ID FROM TB_FIN_GUARANTEE"
                    + " WHERE STATUS_CD = 'ACTIVE' AND END_DT < :asOf ORDER BY GRNT_ID")
            .param("asOf", asOf)
            .query(Long.class)
            .list();
    return new IteratorItemReader<>(ids);
  }

  @Bean
  @StepScope
  public ItemProcessor<Long, Long> guaranteeExpireProcessor(
      GuaranteeExpiryService guaranteeExpiryService,
      Clock clock,
      @Value("#{stepExecution}") StepExecution stepExecution) {
    LocalDate asOf = BatchTargetDate.resolve(stepExecution, clock);
    return grntId -> {
      guaranteeExpiryService.expireGuarantee(grntId, asOf); // 멱등: 이미 EXPIRED면 no-op
      return grntId;
    };
  }

  private static ItemWriter<GuaranteeExpiryDue> noOpWriter() {
    return chunk -> {};
  }

  private static ItemWriter<Long> noOpLongWriter() {
    return chunk -> {};
  }
}
