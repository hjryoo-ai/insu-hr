package com.portfolio.insuhr.batch.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.batch.support.AbstractBatchIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 시나리오 2 — 보수교육 기한 도과 배치 → SUSPENDED 전이 + 이벤트 발행 (설계서 12).
 *
 * <p>{@code eligibilityRefreshJob}이 "쓰기 없이 날짜 경계가 넘어가는 전이"를 잡는지 배관 층에서 검증한다 — Reader가 대상을 집고, 기존
 * reconciler가 판정·전이·발행을 한다. 판정 규칙(경계 inclusive 등)은 Phase 5 도메인 테스트가 이미 지킨다.
 */
class EligibilityRefreshJobIntegrationTest extends AbstractBatchIntegrationTest {

  @Autowired Job eligibilityRefreshJob;

  @Test
  @DisplayName("보수교육 기한 당일은 적격 유지, 다음날 도과분은 자동 SUSPENDED + eligibility.changed 발행")
  void ceDeadlineCrossingSuspendsAndEmitsEvent() throws Exception {
    long orgId = seedOrg();
    long agentId = seedActiveEligibleLifeAgent(seedPerson(), orgId, LocalDate.of(2026, 6, 1));

    // 기한 당일(inclusive): 여전히 적격 → ACTIVE 유지, 종합 판정 불변이라 이벤트 없음.
    JobExecution onDeadline = runJob(eligibilityRefreshJob, LocalDate.of(2026, 6, 1));
    assertThat(isCompleted(onDeadline)).isTrue();
    assertThat(agentStatus(agentId)).as("기한 당일은 적격 유지").isEqualTo("ACTIVE");
    assertThat(recruitEligYn(agentId)).isEqualTo("Y");
    assertThat(outboxCount("AGENT", agentId, "agent.eligibility.changed"))
        .as("판정 불변이라 무발행")
        .isZero();

    // 기한 다음날: 보수교육 도과 → 자동 SUSPENDED + 종합 Y→N 이벤트 (시나리오 2).
    JobExecution afterDeadline = runJob(eligibilityRefreshJob, LocalDate.of(2026, 6, 2));
    assertThat(isCompleted(afterDeadline)).isTrue();
    assertThat(agentStatus(agentId)).as("기한 도과 → 자동 정지").isEqualTo("SUSPENDED");
    assertThat(recruitEligYn(agentId)).isEqualTo("N");
    assertThat(outboxCount("AGENT", agentId, "agent.eligibility.changed"))
        .as("종합 판정 Y→N으로 이벤트 발행")
        .isEqualTo(1);
  }
}
