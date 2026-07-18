package com.portfolio.insuhr.domain.eligibility;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.agent.Agent;
import com.portfolio.insuhr.domain.agent.AgentErrorCode;
import com.portfolio.insuhr.domain.agent.AgentLifecycleService;
import com.portfolio.insuhr.domain.agent.AgentRepository;
import com.portfolio.insuhr.domain.agent.AgentStatus;
import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.integration.IntegrationRecorder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모집자격 판정의 집행자 (설계서 5.4 v1.6).
 *
 * <p>순수 함수 {@link RecruitEligibilityService#evaluate}의 결과를 받아 <b>실제 상태를 바꾸는</b> 쪽이다. 판정(계산)과
 * 집행(전이)을 나눈 이유는 순환을 피하고 배치가 판정 함수를 그대로 재사용하게 하기 위함이다.
 *
 * <p>집행은 셋이다: ① ACTIVE인데 실격이면 자동 SUSPENDED, SUSPENDED인데 회복이면 자동 ACTIVE(상태머신 관문 경유) ② {@code
 * RECRUIT_ELIG_YN}("마지막 계산 결과")을 갱신 ③ <b>종합 판정이 직전과 달라졌을 때만</b> {@code agent.eligibility.changed}를
 * 발행한다 — 쓰기마다 발행하지 않는다. 직전 값 비교 기준이 {@code RECRUIT_ELIG_YN}이다.
 *
 * <p>자격·교육·보증·제재의 모든 쓰기가 이 메서드를 호출한다 — 제재 등록 시 자동 SUSPENDED(7.2)도 별도 코드가 아니라 이 경로다.
 */
@Service
public class AgentEligibilityReconciler {

  private final AgentRepository agentRepository;
  private final RecruitEligibilityService eligibilityService;
  private final AgentLifecycleService lifecycleService;
  private final IntegrationRecorder integrationRecorder;
  private final Clock clock;

  public AgentEligibilityReconciler(
      AgentRepository agentRepository,
      RecruitEligibilityService eligibilityService,
      AgentLifecycleService lifecycleService,
      IntegrationRecorder integrationRecorder,
      Clock clock) {
    this.agentRepository = agentRepository;
    this.eligibilityService = eligibilityService;
    this.lifecycleService = lifecycleService;
    this.integrationRecorder = integrationRecorder;
    this.clock = clock;
  }

  /** 오늘 기준으로 한 설계사의 자격을 재판정하고 상태·캐시·이벤트를 정합화한다. */
  @Transactional
  public EligibilityResult reconcile(Long agentId) {
    return reconcileAsOf(agentId, LocalDate.now(clock));
  }

  /**
   * 지정한 기준일({@code asOf})로 재판정·정합화한다. 배치({@code eligibilityRefreshJob})는 시스템 날짜를 직접 읽지 않고 {@code
   * targetDate}를 이 인자로 넘긴다 — 6.2 앵커 Clock 규약을 배치까지 관통시키고, "같은 {@code targetDate} 재실행 = 같은 결과"라는
   * 멱등성을 성립시킨다({@link com.portfolio.insuhr.domain.emp.AppointmentApplyService#recalculateAsOf}과 같은
   * 패턴).
   *
   * <p>기준일은 판정·전이의 <b>업무 날짜</b>에만 쓴다. "마지막 계산 시각"은 규약대로 실제 실행 시각 {@code Instant.now(clock)}이며
   * (§6.2), 재실행 간 값이 달라지지만 그것은 메타 타임스탬프일 뿐 판정 결과(전이·YN)는 {@code asOf}에만 의존하므로 멱등하다.
   */
  @Transactional
  public EligibilityResult reconcileAsOf(Long agentId, LocalDate asOf) {
    EligibilityResult result = eligibilityService.evaluate(agentId, asOf);

    Agent agent = require(agentId);

    // ① 자동 전이 (ACTIVE↔SUSPENDED). 판정 함수는 상태를 안 바꾸므로 여기서만 상태가 움직인다.
    if (agent.getStatus() == AgentStatus.ACTIVE && !result.substantiveEligible()) {
      lifecycleService.suspend(agentId, asOf, "ELIG_FAIL", reasonText(result));
    } else if (agent.getStatus() == AgentStatus.SUSPENDED && result.substantiveEligible()) {
      lifecycleService.resume(agentId, asOf, "모집자격 회복(자동)");
    }

    // ② 종합 판정 갱신. 종합 YN = 실질 자격 AND 상태 ACTIVE (설계서 5.4 v1.6).
    Agent reloaded = require(agentId); // 위에서 전이했으면 상태가 바뀌어 있다(같은 영속 컨텍스트)
    boolean newFlag = result.substantiveEligible() && reloaded.getStatus() == AgentStatus.ACTIVE;
    boolean changed = newFlag != reloaded.isRecruitEligible();
    reloaded.recordEligibility(newFlag, Instant.now(clock));

    // ③ 종합 판정이 실제로 바뀔 때만 이벤트 (쓰기마다가 아니라).
    if (changed) {
      integrationRecorder.record(
          IntegrationEvent.updated(
              "agent.eligibility.changed",
              "AGENT",
              reloaded.getId(),
              reloaded.getAgentCd(),
              snapshot(reloaded, newFlag, result)));
    }
    return result;
  }

  private String reasonText(EligibilityResult result) {
    return result.commonReasons().stream().map(r -> r.reason()).collect(Collectors.joining(", "));
  }

  private Map<String, Object> snapshot(Agent agent, boolean eligible, EligibilityResult result) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("agentCd", agent.getAgentCd());
    m.put("statusCd", agent.getStatus().name());
    m.put("recruitEligYn", eligible ? "Y" : "N");
    m.put(
        "eligibleLines",
        result.lines().stream()
            .filter(EligibilityResult.LineVerdict::eligible)
            .map(l -> l.line().name())
            .toList());
    return m;
  }

  private Agent require(Long agentId) {
    return agentRepository
        .findById(agentId)
        .orElseThrow(
            () -> new BusinessException(AgentErrorCode.NOT_FOUND, "설계사를 찾을 수 없습니다: " + agentId));
  }
}
