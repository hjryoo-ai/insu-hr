package com.portfolio.insuhr.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 위촉 상태머신 전이표 전 케이스 (설계서 5.3, 13.2 Phase 4 완료 기준).
 *
 * <p>{@link AgentStatus}가 전이표의 단일 원천이므로, 5×5 전 행렬(25칸)을 이 테스트가 <b>독립적으로 선언한 기대표</b>와 대조한다. enum의 맵과
 * 이 테스트의 기대표가 서로를 감시한다 — 한쪽만 고치면 25칸 중 어딘가에서 어긋나 실패한다. 허용 7건, 금지 18건.
 */
class AgentStatusTransitionTest {

  /**
   * 설계서 5.3 전이표를 코드와 독립적으로 다시 적은 것. (enum의 ALLOWED를 그대로 베끼면 검증이 아니라 자기참조가 된다.)
   *
   * <pre>
   *   CANDIDATE     → PENDING_ASSOC
   *   PENDING_ASSOC → ACTIVE
   *   ACTIVE        → SUSPENDED, TERMINATED
   *   SUSPENDED     → ACTIVE, TERMINATED
   *   TERMINATED    → CANDIDATE
   * </pre>
   */
  private static final Map<AgentStatus, Set<AgentStatus>> EXPECTED =
      Map.of(
          AgentStatus.CANDIDATE, Set.of(AgentStatus.PENDING_ASSOC),
          AgentStatus.PENDING_ASSOC, Set.of(AgentStatus.ACTIVE),
          AgentStatus.ACTIVE, Set.of(AgentStatus.SUSPENDED, AgentStatus.TERMINATED),
          AgentStatus.SUSPENDED, Set.of(AgentStatus.ACTIVE, AgentStatus.TERMINATED),
          AgentStatus.TERMINATED, Set.of(AgentStatus.CANDIDATE));

  @Test
  @DisplayName("5×5 전 행렬 — 허용/금지 25칸이 전이표와 정확히 일치한다")
  void fullMatrixMatchesTransitionTable() {
    int allowed = 0;
    int forbidden = 0;
    for (AgentStatus from : AgentStatus.values()) {
      for (AgentStatus to : AgentStatus.values()) {
        boolean expected = EXPECTED.getOrDefault(from, Set.of()).contains(to);
        assertThat(from.canTransitionTo(to))
            .as("전이 %s → %s 는 %s 여야 한다", from, to, expected ? "허용" : "금지")
            .isEqualTo(expected);
        if (expected) {
          allowed++;
        } else {
          forbidden++;
        }
      }
    }
    // 표가 통째로 비거나 전부 허용되는 사고를 막는 총량 확인.
    assertThat(allowed).as("허용 전이 수").isEqualTo(7);
    assertThat(forbidden).as("금지 전이 수 (자기 전이 5 포함)").isEqualTo(18);
  }

  @Test
  @DisplayName("자기 자신으로의 전이는 모두 금지된다")
  void noSelfTransition() {
    for (AgentStatus s : AgentStatus.values()) {
      assertThat(s.canTransitionTo(s)).as("%s → %s 자기 전이 금지", s, s).isFalse();
    }
  }

  @Test
  @DisplayName("TERMINATED에서 갈 수 있는 곳은 CANDIDATE(재위촉)뿐이다")
  void terminatedOnlyToCandidate() {
    assertThat(AgentStatus.TERMINATED.allowedTargets()).containsExactly(AgentStatus.CANDIDATE);
  }
}
