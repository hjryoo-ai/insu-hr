package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** 교차모집 등록 (설계서 6.5). 교차 종목의 판매 등록 이력. */
@Entity
@Table(name = "TB_CROSS_SELL_REG")
public class CrossSellReg extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "CROSS_SELL_ID")
  private Long id;

  @Column(name = "AGENT_ID", nullable = false)
  private Long agentId;

  @Column(name = "TARGET_LINE_CD", nullable = false, length = 30)
  private String targetLineCd;

  @Column(name = "PARTNER_COMPANY_NM", length = 100)
  private String partnerCompanyNm;

  @Column(name = "REG_DT")
  private LocalDate regDt;

  @Column(name = "DEREG_DT")
  private LocalDate deregDt;

  @Column(name = "STATUS_CD", nullable = false, length = 30)
  private String statusCd;

  protected CrossSellReg() {}

  private CrossSellReg(
      Long agentId, LicenseType targetLine, String partnerCompanyNm, LocalDate regDt) {
    this.agentId = agentId;
    this.targetLineCd = targetLine.name();
    this.partnerCompanyNm = partnerCompanyNm;
    this.regDt = regDt;
    this.statusCd = CrossSellStatus.REGISTERED.name();
  }

  public static CrossSellReg register(
      Long agentId, LicenseType targetLine, String partnerCompanyNm, LocalDate regDt) {
    return new CrossSellReg(agentId, targetLine, partnerCompanyNm, regDt);
  }

  public void deregister(LocalDate deregDt) {
    this.deregDt = deregDt;
    this.statusCd = CrossSellStatus.DEREGISTERED.name();
  }

  public Long getId() {
    return id;
  }

  public Long getAgentId() {
    return agentId;
  }

  public LicenseType getTargetLine() {
    return LicenseType.valueOf(targetLineCd);
  }

  public CrossSellStatus getStatus() {
    return CrossSellStatus.valueOf(statusCd);
  }

  public String getPartnerCompanyNm() {
    return partnerCompanyNm;
  }
}
