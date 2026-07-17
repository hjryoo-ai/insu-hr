package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.common.error.ErrorDetail;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.integration.IntegrationRecorder;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설계사 위촉 상태머신 (설계서 5.3). <b>이 서비스가 도메인의 중심이다.</b>
 *
 * <p><b>모든 전이는 이 한 관문을 통과한다.</b> 각 전이 메서드는 ① 사전조건 검사(전이표 + 요건/냉각기간) ② {@code TB_AGENT_APPOINT_HIST}
 * 1행 기록 ③ Outbox 이벤트 발행을 함께 한다(설계서 5.3). recorder 호출을 여기 두는 이유: 전이가 상태머신의 단일 관문이므로 "1 전이 = 1 이력 + 1
 * 이벤트"가 규약이 아니라 <b>구조로</b> 보장된다 — 애플리케이션 서비스 N개에 흩어 두면 하나를 빠뜨릴 수 있다.
 *
 * <p><b>상태를 바꾸는 도메인 서비스라 스스로 {@code @Transactional}(REQUIRED)을 선언한다</b>(설계서 10.1.1 v1.5). 엔티티를 로드해
 * 더티체킹에 맡기므로 트랜잭션이 없으면 flush되지 않는다. "호출부가 트랜잭션을 깔아줄 것"이라는 가정을 두지 않는다 — Phase 7 배치가 직접 불러도 REQUIRED로
 * 안전하다.
 *
 * <p><b>요건검증은 {@link RecruitmentRequirementChecker} SPI로 분리</b>. Phase 4 스텁은 통과, Phase 5가 실판정으로
 * 교체한다 (시나리오 1a/1b 분할의 근거).
 */
@Service
public class AgentLifecycleService {

  private final AgentRepository agentRepository;
  private final AgentAppointHistRepository histRepository;
  private final IntegrationRecorder integrationRecorder;
  private final RecruitmentRequirementChecker requirementChecker;
  private final PolicyConfigService policyConfigService;
  private final Clock clock;

  public AgentLifecycleService(
      AgentRepository agentRepository,
      AgentAppointHistRepository histRepository,
      IntegrationRecorder integrationRecorder,
      RecruitmentRequirementChecker requirementChecker,
      PolicyConfigService policyConfigService,
      Clock clock) {
    this.agentRepository = agentRepository;
    this.histRepository = histRepository;
    this.integrationRecorder = integrationRecorder;
    this.requirementChecker = requirementChecker;
    this.policyConfigService = policyConfigService;
    this.clock = clock;
  }

  /** 업무 기준일. {@code LocalDate.now(clock)} — 업무 날짜라 KST(설계서 6.2 v1.5 파생 규칙). */
  public LocalDate today() {
    return LocalDate.now(clock);
  }

  /**
   * 후보 등록 — 새 설계사를 CANDIDATE로 만든다 (설계서 부록 B 1단계).
   *
   * <p>전이가 아니라 진입점이지만, "1 전이/사건 = 1 이력 + 1 이벤트" 규약을 여기서도 지킨다 — 이력 첫 행({@code null→CANDIDATE})과
   * {@code agent.candidate.registered} 이벤트를 남긴다.
   */
  @Transactional
  public Agent registerCandidate(
      Long personId, String agentCd, Channel channel, Long orgId, Long recruiterAgentId) {
    Agent agent =
        agentRepository.save(Agent.candidate(personId, agentCd, channel, orgId, recruiterAgentId));
    record(agent, null, today(), null, null, "agent.candidate.registered", true);
    return agent;
  }

  /**
   * 위촉 실행: CANDIDATE → PENDING_ASSOC (설계서 부록 B 5단계, 7.2).
   *
   * <p>요건검증을 먼저 한다("요건검증 후 전이" — 7.2). 미충족이면 사유 배열을 담은 422로 던진다(시나리오 1a). 스텁(Phase 4)은 통과시키므로 happy
   * path가 성립하고, Phase 5가 실판정으로 교체하면 같은 코드가 실제 미충족을 422로 낸다.
   */
  @Transactional
  public void appoint(Long agentId, LocalDate appointDt) {
    Agent agent = require(agentId);

    List<ErrorDetail> unmet = requirementChecker.check(agentId);
    if (!unmet.isEmpty()) {
      throw new BusinessException(AgentErrorCode.REQUIREMENT_NOT_MET, unmet);
    }

    AgentStatus from = agent.getStatus();
    agent.appoint(appointDt);
    record(agent, from, appointDt, null, null, "agent.status.changed", false);
  }

  /** 협회 등록번호 수신: PENDING_ASSOC → ACTIVE (설계서 부록 B 6단계). 이벤트 {@code agent.appointed}. */
  @Transactional
  public void registerAssociation(Long agentId, LocalDate eventDt, String assocRegNo) {
    Agent agent = require(agentId);
    AgentStatus from = agent.getStatus();
    agent.activate();
    record(agent, from, eventDt, null, "협회등록번호=" + assocRegNo, "agent.appointed", false);
  }

  /** 모집정지: ACTIVE → SUSPENDED. 사유코드 필수(설계서 5.3 전이표). */
  @Transactional
  public void suspend(Long agentId, LocalDate eventDt, String rsnCd, String rsnTxt) {
    Agent agent = require(agentId);
    AgentStatus from = agent.getStatus();
    agent.suspend();
    record(agent, from, eventDt, rsnCd, rsnTxt, "agent.status.changed", false);
  }

  /** 정지해제: SUSPENDED → ACTIVE. Phase 4는 판정 게이트 없이 수동(설계서 5.3 v1.5). */
  @Transactional
  public void resume(Long agentId, LocalDate eventDt, String rsnTxt) {
    Agent agent = require(agentId);
    AgentStatus from = agent.getStatus();
    agent.resume();
    record(agent, from, eventDt, null, rsnTxt, "agent.status.changed", false);
  }

  /** 해촉: ACTIVE·SUSPENDED → TERMINATED (설계서 부록 B 9단계). 이벤트 {@code agent.terminated}. */
  @Transactional
  public void terminate(Long agentId, LocalDate eventDt, TermReason reason, String rsnTxt) {
    Agent agent = require(agentId);
    AgentStatus from = agent.getStatus();
    agent.terminate(eventDt, reason);
    record(agent, from, eventDt, reason.name(), rsnTxt, "agent.terminated", false);
  }

  /**
   * 재위촉: TERMINATED → CANDIDATE (설계서 부록 B 10단계, 시나리오 4).
   *
   * <p>냉각기간(정책값 {@code REAPPOINT_COOLDOWN_MONTHS})과 금지사유 검사는 엔티티가 한다. 같은 AGENT_ID를 유지하고 현재상태 컬럼만
   * 리셋한다 — 과거 해촉은 이력에 남는다.
   */
  @Transactional
  public void reappoint(Long agentId, String rsnTxt) {
    Agent agent = require(agentId);
    LocalDate today = today();
    int cooldownMonths = policyConfigService.getInt(PolicyKey.REAPPOINT_COOLDOWN_MONTHS, today);

    AgentStatus from = agent.getStatus();
    agent.reappoint(today, cooldownMonths);
    record(agent, from, today, null, rsnTxt, "agent.status.changed", false);
  }

  /** 소속 이동 (설계서 7.2 {@code PUT /agents/{id}/transfer}). 상태 전이가 아니라 소속만. */
  @Transactional
  public void transfer(Long agentId, Long newOrgId, LocalDate eventDt, String rsnTxt) {
    Agent agent = require(agentId);
    AgentStatus status = agent.getStatus();
    agent.transferTo(newOrgId);
    // 소속 이동은 상태가 그대로라 from=to. 이력의 ORG_ID로 "언제 어디로 옮겼는지"가 남는다.
    record(agent, status, eventDt, null, rsnTxt, "agent.transferred", false);
  }

  private Agent require(Long agentId) {
    return agentRepository
        .findById(agentId)
        .orElseThrow(
            () -> new BusinessException(AgentErrorCode.NOT_FOUND, "설계사를 찾을 수 없습니다: " + agentId));
  }

  /**
   * 이력 1행 + Outbox 이벤트를 같은 트랜잭션에서 기록한다 (설계서 5.3, 9.2).
   *
   * @param created true면 생성 이벤트(C), false면 변경 이벤트(U)
   */
  private void record(
      Agent agent,
      AgentStatus from,
      LocalDate eventDt,
      String rsnCd,
      String rsnTxt,
      String eventType,
      boolean created) {
    histRepository.save(
        AgentAppointHist.of(
            agent.getId(), from, agent.getStatus(), eventDt, rsnCd, rsnTxt, agent.getOrgId()));

    IntegrationEvent event =
        created
            ? IntegrationEvent.created(
                eventType, "AGENT", agent.getId(), agent.getAgentCd(), agent.toSnapshot())
            : IntegrationEvent.updated(
                eventType, "AGENT", agent.getId(), agent.getAgentCd(), agent.toSnapshot());
    integrationRecorder.record(event);
  }
}
