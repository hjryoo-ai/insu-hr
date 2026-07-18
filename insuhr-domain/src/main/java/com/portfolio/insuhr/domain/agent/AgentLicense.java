package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** 판매자격 — 종목 하나당 한 행 (설계서 6.4). 모집자격 판정의 종목 게이트. */
@Entity
@Table(name = "TB_AGENT_LICENSE")
public class AgentLicense extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "LICENSE_ID")
  private Long id;

  @Column(name = "AGENT_ID", nullable = false)
  private Long agentId;

  @Column(name = "LICENSE_TYPE_CD", nullable = false, length = 30)
  private String licenseTypeCd;

  @Column(name = "LICENSE_NO", length = 30)
  private String licenseNo;

  @Column(name = "EXAM_PASS_DT")
  private LocalDate examPassDt;

  @Column(name = "REG_DT")
  private LocalDate regDt;

  @Column(name = "STATUS_CD", nullable = false, length = 30)
  private String statusCd;

  protected AgentLicense() {}

  private AgentLicense(
      Long agentId,
      LicenseType type,
      String licenseNo,
      LocalDate examPassDt,
      LocalDate regDt,
      LicenseStatus status) {
    this.agentId = agentId;
    this.licenseTypeCd = type.name();
    this.licenseNo = licenseNo;
    this.examPassDt = examPassDt;
    this.regDt = regDt;
    this.statusCd = status.name();
  }

  public static AgentLicense register(
      Long agentId,
      LicenseType type,
      String licenseNo,
      LocalDate examPassDt,
      LocalDate regDt,
      LicenseStatus status) {
    return new AgentLicense(agentId, type, licenseNo, examPassDt, regDt, status);
  }

  public void changeStatus(LicenseStatus status) {
    this.statusCd = status.name();
  }

  public boolean isValid() {
    return LicenseStatus.VALID.name().equals(statusCd);
  }

  public Long getId() {
    return id;
  }

  public Long getAgentId() {
    return agentId;
  }

  public LicenseType getType() {
    return LicenseType.valueOf(licenseTypeCd);
  }

  public LicenseStatus getStatus() {
    return LicenseStatus.valueOf(statusCd);
  }

  public String getLicenseNo() {
    return licenseNo;
  }

  public LocalDate getExamPassDt() {
    return examPassDt;
  }

  public LocalDate getRegDt() {
    return regDt;
  }
}
