package com.portfolio.insuhr.domain.agent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 설계사 위촉 상태 (설계서 5.3 상태머신).
 *
 * <p><b>이 enum이 전이표의 단일 원천이다 (설계서 5.3 v1.5).</b> 허용 전이를 아래 {@code ALLOWED} 맵 한 곳에만 선언하고, 상태머신 서비스와
 * 엔티티는 이 맵으로만 전이 가능 여부를 묻는다. 전이표가 코드 여기저기 흩어지면 "허용했던가?"를 매번 재확인해야 하지만, 맵이 하나면 단위테스트가 5×5 전 행렬을 이 맵과
 * 대조하는 것으로 표 전체를 못 박는다.
 *
 * <pre>
 *   CANDIDATE      → PENDING_ASSOC
 *   PENDING_ASSOC  → ACTIVE
 *   ACTIVE         → SUSPENDED, TERMINATED
 *   SUSPENDED      → ACTIVE, TERMINATED
 *   TERMINATED     → CANDIDATE            (재위촉 — 같은 AGENT_ID 재사용)
 * </pre>
 */
public enum AgentStatus {
  CANDIDATE,
  PENDING_ASSOC,
  ACTIVE,
  SUSPENDED,
  TERMINATED;

  private static final Map<AgentStatus, Set<AgentStatus>> ALLOWED;

  static {
    Map<AgentStatus, Set<AgentStatus>> m = new EnumMap<>(AgentStatus.class);
    m.put(CANDIDATE, EnumSet.of(PENDING_ASSOC));
    m.put(PENDING_ASSOC, EnumSet.of(ACTIVE));
    m.put(ACTIVE, EnumSet.of(SUSPENDED, TERMINATED));
    m.put(SUSPENDED, EnumSet.of(ACTIVE, TERMINATED));
    m.put(TERMINATED, EnumSet.of(CANDIDATE));
    ALLOWED = m;
  }

  /** {@code this} 에서 {@code target} 으로의 전이가 상태머신 규칙상 허용되는가. */
  public boolean canTransitionTo(AgentStatus target) {
    return ALLOWED.getOrDefault(this, Set.of()).contains(target);
  }

  /** 이 상태에서 갈 수 있는 상태들. 테스트가 전 행렬을 대조하는 데 쓴다. */
  public Set<AgentStatus> allowedTargets() {
    return ALLOWED.getOrDefault(this, Set.of());
  }
}
