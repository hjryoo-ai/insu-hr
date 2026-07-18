package com.portfolio.insuhr.batch.guarantee;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.batch.support.AbstractBatchIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code guaranteeExpiryJob} — D-30 알림 조건부 발행 + D-0 EXPIRED 물질화(→reconcile) + 재실행 멱등 (설계서 8 v2.0).
 */
class GuaranteeExpiryJobIntegrationTest extends AbstractBatchIntegrationTest {

  @Autowired Job guaranteeExpiryJob;

  @Test
  @DisplayName("만료 30일 이내 보증에 D-30 알림 1건 + notice.created, 재실행은 no-op")
  void enqueuesD30NoticeOnceAndConditionalEvent() throws Exception {
    LocalDate targetDate = LocalDate.of(2026, 7, 18);
    long orgId = seedOrg();
    long agentId = seedBareActiveAgent(seedPerson(), orgId);
    long grntId = seedGuarantee(agentId, targetDate.plusDays(20), "ACTIVE"); // 30일 창 안

    JobExecution first = runJob(guaranteeExpiryJob, targetDate);
    assertThat(isCompleted(first)).isTrue();
    assertThat(noticeCount("GUARANTEE_EXPIRY", grntId, "D30")).isEqualTo(1);
    assertThat(guaranteeStatus(grntId)).as("아직 유효 → 물질화 안 함").isEqualTo("ACTIVE");
    assertThat(outboxCount("AGENT", agentId, "notice.created")).as("행 생성 시에만 발행").isEqualTo(1);

    JobExecution second = runJob(guaranteeExpiryJob, targetDate);
    assertThat(isCompleted(second)).isTrue();
    assertThat(noticeCount("GUARANTEE_EXPIRY", grntId, "D30")).as("dedup — 중복 없음").isEqualTo(1);
    assertThat(outboxCount("AGENT", agentId, "notice.created"))
        .as("재실행은 enqueue 0 → 무발행")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("END_DT 지난 보증은 EXPIRED 물질화 + reconcile로 자동 SUSPENDED + eligibility.changed, 재실행 멱등")
  void expiresPastGuaranteeAndTriggersReconcile() throws Exception {
    LocalDate targetDate = LocalDate.of(2026, 7, 18);
    long orgId = seedOrg();
    // 유일한 보증이 어제 만료 — 그 밖의 게이트(자격/협회/보수교육)는 전부 열려 있어 만료만이 실격 변수.
    long agentId =
        seedActiveEligibleLifeAgent(
            seedPerson(), orgId, LocalDate.of(2030, 1, 1), targetDate.minusDays(1));
    long grntId =
        jdbcClient
            .sql("SELECT GRNT_ID FROM TB_FIN_GUARANTEE WHERE AGENT_ID = :a")
            .param("a", agentId)
            .query(Long.class)
            .single();

    JobExecution first = runJob(guaranteeExpiryJob, targetDate);
    assertThat(isCompleted(first)).isTrue();
    assertThat(guaranteeStatus(grntId)).as("END_DT < targetDate → EXPIRED").isEqualTo("EXPIRED");
    assertThat(agentStatus(agentId)).as("유효 보증 소멸 → 자동 정지").isEqualTo("SUSPENDED");
    assertThat(recruitEligYn(agentId)).isEqualTo("N");
    assertThat(outboxCount("AGENT", agentId, "agent.eligibility.changed"))
        .as("만료 처리가 reconcile을 유발 — 종합 Y→N 발행")
        .isEqualTo(1);
    assertThat(noticeCount("GUARANTEE_EXPIRY", grntId)).as("만료분은 알림 창 밖").isZero();

    JobExecution second = runJob(guaranteeExpiryJob, targetDate);
    assertThat(isCompleted(second)).isTrue();
    assertThat(guaranteeStatus(grntId)).isEqualTo("EXPIRED");
    assertThat(agentStatus(agentId)).isEqualTo("SUSPENDED");
    assertThat(outboxCount("AGENT", agentId, "agent.eligibility.changed"))
        .as("이미 EXPIRED → 물질화·reconcile 안 함 → 이벤트 불변")
        .isEqualTo(1);
  }
}
