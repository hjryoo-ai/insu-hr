package com.portfolio.insuhr.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.common.exception.BusinessException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 설계사 엔티티의 전이 가드·재위촉 의미론 단위 테스트 (설계서 5.3 v1.5).
 *
 * <p>DB·Spring 없이 상태머신 규칙만 검증한다. "마스터=현재 상태, 과거는 이력에만"이라는 재위촉 원칙을 여기서 못 박는다.
 */
class AgentTest {

  private static final int COOLDOWN_MONTHS = 6;

  private Agent candidate() {
    return Agent.candidate(1L, "A00000001", Channel.FC, 10L, null);
  }

  /** 후보 → 위촉 → 협회등록 → 해촉 까지 몰고 간 설계사. */
  private Agent terminated(LocalDate appointDt, LocalDate terminateDt, TermReason reason) {
    Agent agent = candidate();
    agent.appoint(appointDt);
    agent.activate();
    agent.terminate(terminateDt, reason);
    return agent;
  }

  @Test
  @DisplayName("위촉은 최초 위촉일과 최근 위촉일을 채우고 PENDING_ASSOC로 간다")
  void appointSetsDatesAndStatus() {
    Agent agent = candidate();
    agent.appoint(LocalDate.of(2026, 3, 1));

    assertThat(agent.getStatus()).isEqualTo(AgentStatus.PENDING_ASSOC);
    assertThat(agent.getFirstAppointDt()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(agent.getLastAppointDt()).isEqualTo(LocalDate.of(2026, 3, 1));
  }

  @Test
  @DisplayName("전이표에 없는 전이는 거부된다 — CANDIDATE는 곧바로 정지될 수 없다")
  void illegalTransitionRejected() {
    assertThatThrownBy(() -> candidate().suspend())
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("허용되지 않는 위촉 전이");
  }

  @Test
  @DisplayName("정상 생애주기를 끝까지 통과한다: 후보→위촉→활성→정지→해제→해촉")
  void fullLifecycle() {
    Agent agent = candidate();
    assertThatCode(
            () -> {
              agent.appoint(LocalDate.of(2026, 1, 1));
              agent.activate();
              agent.suspend();
              agent.resume();
              agent.terminate(LocalDate.of(2026, 6, 1), TermReason.SELF);
            })
        .doesNotThrowAnyException();
    assertThat(agent.getStatus()).isEqualTo(AgentStatus.TERMINATED);
    assertThat(agent.getTerminateDt()).isEqualTo(LocalDate.of(2026, 6, 1));
    assertThat(agent.getTerminateRsnCd()).isEqualTo("SELF");
  }

  @Test
  @DisplayName("재위촉은 같은 상태 컬럼을 리셋하되 최초 위촉일은 보존한다 (마스터=현재, 과거는 이력에만)")
  void reappointResetsCurrentStateButKeepsFirstAppointDt() {
    Agent agent = terminated(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1), TermReason.SELF);

    // 냉각기간(6개월)이 지난 시점에 재위촉.
    agent.reappoint(LocalDate.of(2026, 1, 2), COOLDOWN_MONTHS);

    assertThat(agent.getStatus()).isEqualTo(AgentStatus.CANDIDATE);
    assertThat(agent.getTerminateDt()).as("해촉일은 현재상태에서 지운다").isNull();
    assertThat(agent.getTerminateRsnCd()).as("해촉사유도 지운다").isNull();
    assertThat(agent.isRecruitEligible()).as("모집자격 캐시 리셋").isFalse();
    assertThat(agent.getFirstAppointDt())
        .as("최초 위촉일은 불변 사실이라 보존")
        .isEqualTo(LocalDate.of(2025, 1, 1));
  }

  @Test
  @DisplayName("냉각기간이 지나지 않으면 재위촉을 거부한다")
  void reappointBlockedByCooldown() {
    Agent agent = terminated(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1), TermReason.SELF);

    // 해촉 6/1 + 6개월 = 12/1 이 재위촉 가능일. 그 하루 전은 거부.
    assertThatThrownBy(() -> agent.reappoint(LocalDate.of(2025, 11, 30), COOLDOWN_MONTHS))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("재위촉 제한기간");
  }

  @Test
  @DisplayName("재위촉 가능일 당일에는 재위촉된다 (경계)")
  void reappointAllowedOnBoundaryDate() {
    Agent agent = terminated(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 1), TermReason.SELF);

    assertThatCode(() -> agent.reappoint(LocalDate.of(2025, 12, 1), COOLDOWN_MONTHS))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("징계해촉은 냉각기간과 무관하게 재위촉이 영구 거부된다")
  void disciplineTerminationForbidsReappointForever() {
    Agent agent =
        terminated(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 1), TermReason.DISCIPLINE);

    // 5년도 더 지났지만(냉각기간 훨씬 초과) 사유가 징계면 거부.
    assertThatThrownBy(() -> agent.reappoint(LocalDate.of(2026, 1, 1), COOLDOWN_MONTHS))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("재위촉이 금지된 해촉사유");
  }
}
