package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 재정보증 (설계서 6.4, 5.4).
 *
 * <p>유효 보증은 <b>합산</b>해 최소보증금액(정책값)과 비교한다(설계서 5.4 v1.6). 유효 기준은 상태 ACTIVE + 기간 내(경계 포함, {@code
 * END_DT >= 오늘}).
 */
@Entity
@Table(name = "TB_FIN_GUARANTEE")
public class FinGuarantee extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "GRNT_ID")
  private Long id;

  @Column(name = "AGENT_ID", nullable = false)
  private Long agentId;

  @Column(name = "GRNT_TYPE_CD", nullable = false, length = 30)
  private String grntTypeCd;

  @Column(name = "GRNT_AMT", nullable = false, precision = 15)
  private BigDecimal grntAmt;

  @Column(name = "ISSUER_NM", length = 100)
  private String issuerNm;

  @Column(name = "POLICY_NO", length = 50)
  private String policyNo;

  @Column(name = "START_DT", nullable = false)
  private LocalDate startDt;

  @Column(name = "END_DT", nullable = false)
  private LocalDate endDt;

  @Column(name = "STATUS_CD", nullable = false, length = 30)
  private String statusCd;

  protected FinGuarantee() {}

  private FinGuarantee(
      Long agentId,
      String grntTypeCd,
      BigDecimal grntAmt,
      String issuerNm,
      String policyNo,
      LocalDate startDt,
      LocalDate endDt,
      GuaranteeStatus status) {
    this.agentId = agentId;
    this.grntTypeCd = grntTypeCd;
    this.grntAmt = grntAmt;
    this.issuerNm = issuerNm;
    this.policyNo = policyNo;
    this.startDt = startDt;
    this.endDt = endDt;
    this.statusCd = status.name();
  }

  public static FinGuarantee register(
      Long agentId,
      String grntTypeCd,
      BigDecimal grntAmt,
      String issuerNm,
      String policyNo,
      LocalDate startDt,
      LocalDate endDt,
      GuaranteeStatus status) {
    return new FinGuarantee(
        agentId, grntTypeCd, grntAmt, issuerNm, policyNo, startDt, endDt, status);
  }

  public void changeStatus(GuaranteeStatus status) {
    this.statusCd = status.name();
  }

  /** 기준일에 유효한가 — 상태 ACTIVE + 기간 내(경계 포함). */
  public boolean isActiveOn(LocalDate asOf) {
    return GuaranteeStatus.ACTIVE.name().equals(statusCd)
        && !asOf.isBefore(startDt)
        && !asOf.isAfter(endDt);
  }

  public Long getId() {
    return id;
  }

  public Long getAgentId() {
    return agentId;
  }

  public BigDecimal getGrntAmt() {
    return grntAmt;
  }

  public LocalDate getStartDt() {
    return startDt;
  }

  public LocalDate getEndDt() {
    return endDt;
  }

  public GuaranteeStatus getStatus() {
    return GuaranteeStatus.valueOf(statusCd);
  }

  public String getGrntTypeCd() {
    return grntTypeCd;
  }
}
