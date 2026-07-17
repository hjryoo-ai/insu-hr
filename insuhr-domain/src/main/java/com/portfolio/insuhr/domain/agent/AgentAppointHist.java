package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 위촉상태 전이 이력 (설계서 5.3, 6.5).
 *
 * <p>상태머신의 모든 전이가 여기 1행씩 남긴다 — 이 행이 있어야 "마스터=현재 상태"라는 리셋 가능한 마스터가 성립한다. 재위촉이 {@code TB_AGENT}의
 * {@code TERMINATE_DT}를 지워도 "언제 왜 해촉됐는지"는 이 표에 남는다(설계서 5.3 v1.5).
 *
 * <p>append-only다 — 수정 메서드가 없다. 잘못 냈으면 정정 전이를 새로 기록한다(발령의 정정 발령과 같은 원리).
 */
@Entity
@Table(name = "TB_AGENT_APPOINT_HIST")
public class AgentAppointHist extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "HIST_ID")
  private Long id;

  @Column(name = "AGENT_ID", nullable = false)
  private Long agentId;

  /** 전이 전 상태. 최초 후보등록이면 null. */
  @Column(name = "FROM_STATUS_CD", length = 30)
  private String fromStatusCd;

  @Column(name = "TO_STATUS_CD", nullable = false, length = 30)
  private String toStatusCd;

  @Column(name = "EVENT_DT", nullable = false)
  private LocalDate eventDt;

  @Column(name = "RSN_CD", length = 30)
  private String rsnCd;

  @Column(name = "RSN_TXT", length = 400)
  private String rsnTxt;

  /** 전이 당시 소속. 소속 이동 추적용. */
  @Column(name = "ORG_ID")
  private Long orgId;

  protected AgentAppointHist() {}

  private AgentAppointHist(
      Long agentId,
      AgentStatus from,
      AgentStatus to,
      LocalDate eventDt,
      String rsnCd,
      String rsnTxt,
      Long orgId) {
    this.agentId = agentId;
    this.fromStatusCd = from == null ? null : from.name();
    this.toStatusCd = to.name();
    this.eventDt = eventDt;
    this.rsnCd = rsnCd;
    this.rsnTxt = rsnTxt;
    this.orgId = orgId;
  }

  /** 전이 1건을 기록한다. {@code from}이 null이면 최초 후보등록. */
  public static AgentAppointHist of(
      Long agentId,
      AgentStatus from,
      AgentStatus to,
      LocalDate eventDt,
      String rsnCd,
      String rsnTxt,
      Long orgId) {
    return new AgentAppointHist(agentId, from, to, eventDt, rsnCd, rsnTxt, orgId);
  }

  public Long getId() {
    return id;
  }

  public Long getAgentId() {
    return agentId;
  }

  public AgentStatus getFromStatus() {
    return fromStatusCd == null ? null : AgentStatus.valueOf(fromStatusCd);
  }

  public AgentStatus getToStatus() {
    return AgentStatus.valueOf(toStatusCd);
  }

  public LocalDate getEventDt() {
    return eventDt;
  }

  public String getRsnCd() {
    return rsnCd;
  }

  public String getRsnTxt() {
    return rsnTxt;
  }

  public Long getOrgId() {
    return orgId;
  }
}
