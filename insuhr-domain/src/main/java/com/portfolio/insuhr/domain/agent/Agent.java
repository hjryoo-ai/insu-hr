package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.support.BaseEntity;
import com.portfolio.insuhr.domain.support.YnConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 설계사 마스터 (설계서 5.3, 6.4).
 *
 * <p><b>마스터는 현재 상태만 담는다.</b> 상태가 어떻게 바뀌어 왔는지는 {@code TB_AGENT_APPOINT_HIST}에 append-only로 남는다. 그래서
 * 재위촉이 현재상태 컬럼을 리셋해도 과거는 이력에 온전하다 — 이 "마스터=현재, 과거는 이력에만" 원칙을 재위촉에서 처음 시험한다(설계서 5.3 v1.5).
 *
 * <p><b>전이는 {@link AgentStatus}의 전이표를 통과해야 한다.</b> 각 전이 메서드는 {@link #requireTransition}으로 from→to를
 * 그 맵에 물어 불법 전이를 막는다. 전이표가 코드의 단일 원천이므로 "이 전이 허용했던가"를 엔티티가 따로 판단하지 않는다.
 *
 * <p><b>{@link Version} 낙관적 잠금.</b> 두 담당자가 동시에 정지·해촉을 걸면 사전조건 검사와 쓰기 사이의 틈에서 불법 전이가 성립할 수 있다. 버전
 * 충돌로 진 쪽이 예외를 받아 409가 된다(설계서 5.3 v1.5).
 */
@Entity
@Table(name = "TB_AGENT")
public class Agent extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "AGENT_ID")
  private Long id;

  @Column(name = "PERSON_ID", nullable = false)
  private Long personId;

  @Column(name = "AGENT_CD", nullable = false, length = 10)
  private String agentCd;

  @Column(name = "CHANNEL_CD", nullable = false, length = 30)
  private String channelCd;

  @Column(name = "ORG_ID", nullable = false)
  private Long orgId;

  @Column(name = "AGENT_STATUS_CD", nullable = false, length = 30)
  private String agentStatusCd;

  @Column(name = "FIRST_APPOINT_DT")
  private LocalDate firstAppointDt;

  @Column(name = "LAST_APPOINT_DT")
  private LocalDate lastAppointDt;

  @Column(name = "TERMINATE_DT")
  private LocalDate terminateDt;

  @Column(name = "TERMINATE_RSN_CD", length = 30)
  private String terminateRsnCd;

  @Column(name = "RECRUITER_AGENT_ID")
  private Long recruiterAgentId;

  @Convert(converter = YnConverter.class)
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "RECRUIT_ELIG_YN", nullable = false, length = 1)
  private boolean recruitEligible;

  @Column(name = "ELIG_CHECKED_AT")
  private Instant eligCheckedAt;

  @Version
  @Column(name = "VERSION", nullable = false)
  private long version;

  protected Agent() {}

  private Agent(Long personId, String agentCd, Channel channel, Long orgId, Long recruiterAgentId) {
    this.personId = personId;
    this.agentCd = agentCd;
    this.channelCd = channel.name();
    this.orgId = orgId;
    this.recruiterAgentId = recruiterAgentId;
    this.agentStatusCd = AgentStatus.CANDIDATE.name();
    this.recruitEligible = false;
  }

  /**
   * 후보 등록 — 새 설계사를 CANDIDATE 상태로 만든다 (설계서 5.3 진입점).
   *
   * @param recruiterAgentId 도입자. 없으면 null (계보 루트)
   */
  public static Agent candidate(
      Long personId, String agentCd, Channel channel, Long orgId, Long recruiterAgentId) {
    return new Agent(personId, agentCd, channel, orgId, recruiterAgentId);
  }

  /** 위촉 실행: CANDIDATE → PENDING_ASSOC. 요건검증은 상태머신 서비스가 이 호출 전에 SPI로 한다(설계서 5.3 v1.5). */
  public void appoint(LocalDate appointDt) {
    requireTransition(AgentStatus.PENDING_ASSOC);
    this.lastAppointDt = appointDt;
    if (this.firstAppointDt == null) {
      this.firstAppointDt = appointDt; // 최초 위촉일은 이후 재위촉에도 불변
    }
    this.agentStatusCd = AgentStatus.PENDING_ASSOC.name();
  }

  /** 협회 등록번호 수신: PENDING_ASSOC → ACTIVE (모집 가능). */
  public void activate() {
    requireTransition(AgentStatus.ACTIVE);
    this.agentStatusCd = AgentStatus.ACTIVE.name();
  }

  /** 모집정지: ACTIVE → SUSPENDED. */
  public void suspend() {
    requireTransition(AgentStatus.SUSPENDED);
    this.agentStatusCd = AgentStatus.SUSPENDED.name();
  }

  /**
   * 정지해제: SUSPENDED → ACTIVE.
   *
   * <p><b>Phase 4는 판정 게이트 없이 수동으로 연다.</b> "정지 사유 전부 해소"라는 올바른 복귀 조건은 사유의 다중성(제재+교육미이수)을 상태 컬럼 하나로
   * 추적할 수 없어 판정 서비스가 도출하는 Phase 5 소관이다(설계서 5.3 v1.5).
   */
  public void resume() {
    requireTransition(AgentStatus.ACTIVE);
    this.agentStatusCd = AgentStatus.ACTIVE.name();
  }

  /** 해촉: ACTIVE·SUSPENDED → TERMINATED. 해촉일·사유코드 필수(설계서 5.3 전이표). */
  public void terminate(LocalDate terminateDt, TermReason reason) {
    requireTransition(AgentStatus.TERMINATED);
    this.terminateDt = terminateDt;
    this.terminateRsnCd = reason.name();
    this.agentStatusCd = AgentStatus.TERMINATED.name();
  }

  /**
   * 재위촉: TERMINATED → CANDIDATE, 같은 AGENT_ID 재사용 (설계서 5.3 v1.5).
   *
   * <p>냉각기간(정책값)과 금지사유를 검사한 뒤 현재상태 컬럼을 정리한다 — {@code TERMINATE_DT}/{@code RSN}은 NULL로(이력엔 남는다),
   * {@code RECRUIT_ELIG_YN}은 'N'으로. {@code FIRST_APPOINT_DT}는 최초 위촉일이라 보존하고, {@code
   * LAST_APPOINT_DT}는 이후 실제 위촉({@link #appoint})이 갱신한다.
   *
   * @param today 업무 기준일 (주입된 Clock에서)
   * @param cooldownMonths 재위촉 제한기간(개월). 정책값 {@code REAPPOINT_COOLDOWN_MONTHS}
   */
  public void reappoint(LocalDate today, int cooldownMonths) {
    if (currentTermReason() != null && currentTermReason().isReappointBlocked()) {
      throw new BusinessException(
          AgentErrorCode.REAPPOINT_FORBIDDEN,
          "재위촉이 금지된 해촉사유입니다. 사유=" + terminateRsnCd + " (냉각기간과 무관하게 영구 거부)");
    }
    if (terminateDt != null && today.isBefore(terminateDt.plusMonths(cooldownMonths))) {
      throw new BusinessException(
          AgentErrorCode.REAPPOINT_COOLDOWN,
          "재위촉 제한기간이 지나지 않았습니다. 해촉일="
              + terminateDt
              + ", 제한기간="
              + cooldownMonths
              + "개월, 재위촉 가능일="
              + terminateDt.plusMonths(cooldownMonths));
    }
    requireTransition(AgentStatus.CANDIDATE);
    this.terminateDt = null;
    this.terminateRsnCd = null;
    this.recruitEligible = false;
    this.agentStatusCd = AgentStatus.CANDIDATE.name();
  }

  /** 소속 이동 (설계서 7.2 {@code PUT /agents/{id}/transfer}). 상태 전이가 아니라 소속만 바뀐다. */
  public void transferTo(Long newOrgId) {
    this.orgId = newOrgId;
  }

  private void requireTransition(AgentStatus target) {
    AgentStatus from = getStatus();
    if (!from.canTransitionTo(target)) {
      throw new BusinessException(
          AgentErrorCode.ILLEGAL_TRANSITION,
          "허용되지 않는 위촉 전이입니다: " + from + " → " + target + " (설계서 5.3 전이표)");
    }
  }

  private TermReason currentTermReason() {
    return terminateRsnCd == null ? null : TermReason.valueOf(terminateRsnCd);
  }

  /** 이벤트·이력에 실을 전체 상태 스냅샷 (설계서 9.3 — 민감정보 금지, 업무키·상태만). */
  public Map<String, Object> toSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("agentId", id);
    snapshot.put("agentCd", agentCd);
    snapshot.put("channelCd", channelCd);
    snapshot.put("orgId", orgId);
    snapshot.put("statusCd", agentStatusCd);
    snapshot.put("firstAppointDt", firstAppointDt == null ? null : firstAppointDt.toString());
    snapshot.put("lastAppointDt", lastAppointDt == null ? null : lastAppointDt.toString());
    snapshot.put("terminateDt", terminateDt == null ? null : terminateDt.toString());
    snapshot.put("terminateRsnCd", terminateRsnCd);
    snapshot.put("recruiterAgentId", recruiterAgentId);
    return snapshot;
  }

  public Long getId() {
    return id;
  }

  public Long getPersonId() {
    return personId;
  }

  public String getAgentCd() {
    return agentCd;
  }

  public Channel getChannel() {
    return Channel.valueOf(channelCd);
  }

  public Long getOrgId() {
    return orgId;
  }

  public AgentStatus getStatus() {
    return AgentStatus.valueOf(agentStatusCd);
  }

  public LocalDate getFirstAppointDt() {
    return firstAppointDt;
  }

  public LocalDate getLastAppointDt() {
    return lastAppointDt;
  }

  public LocalDate getTerminateDt() {
    return terminateDt;
  }

  public String getTerminateRsnCd() {
    return terminateRsnCd;
  }

  public Long getRecruiterAgentId() {
    return recruiterAgentId;
  }

  public boolean isRecruitEligible() {
    return recruitEligible;
  }

  public long getVersion() {
    return version;
  }
}
