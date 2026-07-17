package com.portfolio.insuhr.domain.emp;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 인사기록카드 — 직원 보유 자격증 (설계서 6.5).
 *
 * <p>설계사 판매자격({@code TB_AGENT_LICENSE}, Phase 5)과 이름이 비슷하지만 무관하다. 이쪽은 모집자격 판정에 쓰이지 않는 단순 기록이다.
 */
@Entity
@Table(name = "TB_EMP_CERT")
public class EmpCert extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "CERT_ID")
  private Long id;

  @Column(name = "EMP_ID", nullable = false)
  private Long empId;

  @Column(name = "CERT_NM", nullable = false, length = 100)
  private String certNm;

  @Column(name = "ISSUER_NM", length = 100)
  private String issuerNm;

  @Column(name = "CERT_NO", length = 50)
  private String certNo;

  @Column(name = "ACQUIRE_DT", nullable = false)
  private LocalDate acquireDt;

  @Column(name = "EXPIRE_DT")
  private LocalDate expireDt;

  protected EmpCert() {}

  public EmpCert(
      Long empId,
      String certNm,
      String issuerNm,
      String certNo,
      LocalDate acquireDt,
      LocalDate expireDt) {
    this.empId = empId;
    this.certNm = certNm;
    this.issuerNm = issuerNm;
    this.certNo = certNo;
    this.acquireDt = acquireDt;
    this.expireDt = expireDt;
    validatePeriod();
  }

  public void update(
      String certNm, String issuerNm, String certNo, LocalDate acquireDt, LocalDate expireDt) {
    this.certNm = certNm;
    this.issuerNm = issuerNm;
    this.certNo = certNo;
    this.acquireDt = acquireDt;
    this.expireDt = expireDt;
    validatePeriod();
  }

  private void validatePeriod() {
    if (expireDt != null && expireDt.isBefore(acquireDt)) {
      throw new IllegalArgumentException("만료일이 취득일보다 앞설 수 없습니다: " + certNm);
    }
  }

  public boolean isValidOn(LocalDate asOf) {
    return !asOf.isBefore(acquireDt) && (expireDt == null || !asOf.isAfter(expireDt));
  }

  public Long getId() {
    return id;
  }

  public Long getEmpId() {
    return empId;
  }

  public String getCertNm() {
    return certNm;
  }

  public String getIssuerNm() {
    return issuerNm;
  }

  public String getCertNo() {
    return certNo;
  }

  public LocalDate getAcquireDt() {
    return acquireDt;
  }

  public LocalDate getExpireDt() {
    return expireDt;
  }
}
