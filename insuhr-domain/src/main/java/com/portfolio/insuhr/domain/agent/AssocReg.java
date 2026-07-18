package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** 협회 등록/말소 이력 (설계서 6.5, 5.4). 종목 게이트의 협회 등록 유효성을 판정한다. */
@Entity
@Table(name = "TB_ASSOC_REG")
public class AssocReg extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ASSOC_REG_ID")
  private Long id;

  @Column(name = "AGENT_ID", nullable = false)
  private Long agentId;

  @Column(name = "ASSOC_CD", nullable = false, length = 30)
  private String assocCd;

  @Column(name = "ASSOC_REG_NO", length = 50)
  private String assocRegNo;

  @Column(name = "REG_DT")
  private LocalDate regDt;

  @Column(name = "DEREG_DT")
  private LocalDate deregDt;

  @Column(name = "DEREG_RSN_CD", length = 30)
  private String deregRsnCd;

  @Column(name = "STATUS_CD", nullable = false, length = 30)
  private String statusCd;

  protected AssocReg() {}

  private AssocReg(
      Long agentId, Association assoc, String assocRegNo, LocalDate regDt, AssocRegStatus status) {
    this.agentId = agentId;
    this.assocCd = assoc.name();
    this.assocRegNo = assocRegNo;
    this.regDt = regDt;
    this.statusCd = status.name();
  }

  public static AssocReg register(
      Long agentId, Association assoc, String assocRegNo, LocalDate regDt) {
    return new AssocReg(agentId, assoc, assocRegNo, regDt, AssocRegStatus.REGISTERED);
  }

  /** 말소한다. */
  public void deregister(LocalDate deregDt, String deregRsnCd) {
    this.deregDt = deregDt;
    this.deregRsnCd = deregRsnCd;
    this.statusCd = AssocRegStatus.DEREGISTERED.name();
  }

  public boolean isRegistered() {
    return AssocRegStatus.REGISTERED.name().equals(statusCd);
  }

  public Association getAssoc() {
    return Association.valueOf(assocCd);
  }

  public Long getId() {
    return id;
  }

  public Long getAgentId() {
    return agentId;
  }

  public AssocRegStatus getStatus() {
    return AssocRegStatus.valueOf(statusCd);
  }

  public String getAssocRegNo() {
    return assocRegNo;
  }

  public LocalDate getRegDt() {
    return regDt;
  }

  public LocalDate getDeregDt() {
    return deregDt;
  }
}
