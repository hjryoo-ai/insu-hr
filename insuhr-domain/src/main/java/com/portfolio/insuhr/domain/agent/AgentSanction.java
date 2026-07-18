package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.domain.support.BaseEntity;
import com.portfolio.insuhr.domain.support.YnConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 제재/징계 (설계서 6.5, 5.4).
 *
 * <p>{@code RECRUIT_BLOCK_YN='Y'}이고 기간이 활성이면 모집정지 사유다 — reconciler가 이를 보고 ACTIVE→SUSPENDED로 자동
 * 전이한다(설계서 5.4 v1.6). 활성 기준은 {@code START_DT <= 오늘 <= END_DT}(양끝 포함), {@code END_DT} null이면 무기한.
 */
@Entity
@Table(name = "TB_AGENT_SANCTION")
public class AgentSanction extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "SANCTION_ID")
  private Long id;

  @Column(name = "AGENT_ID", nullable = false)
  private Long agentId;

  @Column(name = "SANCTION_ORG_CD", nullable = false, length = 30)
  private String sanctionOrgCd;

  @Column(name = "SANCTION_TYPE_CD", nullable = false, length = 30)
  private String sanctionTypeCd;

  @Column(name = "START_DT", nullable = false)
  private LocalDate startDt;

  @Column(name = "END_DT")
  private LocalDate endDt;

  @Column(name = "RSN_TXT", length = 400)
  private String rsnTxt;

  @Convert(converter = YnConverter.class)
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "RECRUIT_BLOCK_YN", nullable = false, length = 1)
  private boolean recruitBlock;

  protected AgentSanction() {}

  private AgentSanction(
      Long agentId,
      String sanctionOrgCd,
      String sanctionTypeCd,
      LocalDate startDt,
      LocalDate endDt,
      String rsnTxt,
      boolean recruitBlock) {
    this.agentId = agentId;
    this.sanctionOrgCd = sanctionOrgCd;
    this.sanctionTypeCd = sanctionTypeCd;
    this.startDt = startDt;
    this.endDt = endDt;
    this.rsnTxt = rsnTxt;
    this.recruitBlock = recruitBlock;
  }

  public static AgentSanction impose(
      Long agentId,
      String sanctionOrgCd,
      String sanctionTypeCd,
      LocalDate startDt,
      LocalDate endDt,
      String rsnTxt,
      boolean recruitBlock) {
    return new AgentSanction(
        agentId, sanctionOrgCd, sanctionTypeCd, startDt, endDt, rsnTxt, recruitBlock);
  }

  /** 제재를 종료(해제)한다 — 종료일을 찍는다. */
  public void lift(LocalDate endDt) {
    this.endDt = endDt;
  }

  /** 기준일에 모집정지를 거는 활성 제재인가 — RECRUIT_BLOCK + 기간(양끝 포함, END null=무기한). */
  public boolean isBlockingOn(LocalDate asOf) {
    if (!recruitBlock) {
      return false;
    }
    if (asOf.isBefore(startDt)) {
      return false;
    }
    return endDt == null || !asOf.isAfter(endDt);
  }

  public Long getId() {
    return id;
  }

  public Long getAgentId() {
    return agentId;
  }

  public LocalDate getStartDt() {
    return startDt;
  }

  public LocalDate getEndDt() {
    return endDt;
  }

  public boolean isRecruitBlock() {
    return recruitBlock;
  }

  public String getSanctionOrgCd() {
    return sanctionOrgCd;
  }

  public String getSanctionTypeCd() {
    return sanctionTypeCd;
  }

  public String getRsnTxt() {
    return rsnTxt;
  }
}
