package com.portfolio.insuhr.domain.emp;

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
 * 인사기록카드 — 직원 교육 이수 (설계서 6.5).
 *
 * <p>설계사 보수교육({@code TB_AGENT_EDU}, Phase 5)과 별개다. 이쪽에는 {@code NEXT_DUE_DT} 계산이 없다 — 직원 교육은 모집자격
 * 판정의 입력이 아니기 때문이다.
 */
@Entity
@Table(name = "TB_EMP_EDU")
public class EmpEdu extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "EMP_EDU_ID")
  private Long id;

  @Column(name = "EMP_ID", nullable = false)
  private Long empId;

  @Column(name = "EDU_NM", nullable = false, length = 100)
  private String eduNm;

  @Column(name = "EDU_TYPE_CD", nullable = false, length = 30)
  private String eduTypeCd;

  @Column(name = "START_DT", nullable = false)
  private LocalDate startDt;

  @Column(name = "END_DT", nullable = false)
  private LocalDate endDt;

  @Column(name = "EDU_HOURS", precision = 5, scale = 1)
  private BigDecimal eduHours;

  @Column(name = "RESULT_CD", nullable = false, length = 30)
  private String resultCd;

  protected EmpEdu() {}

  public EmpEdu(
      Long empId,
      String eduNm,
      String eduTypeCd,
      LocalDate startDt,
      LocalDate endDt,
      BigDecimal eduHours,
      String resultCd) {
    this.empId = empId;
    this.eduNm = eduNm;
    this.eduTypeCd = eduTypeCd;
    this.startDt = startDt;
    this.endDt = endDt;
    this.eduHours = eduHours;
    this.resultCd = resultCd;
    validatePeriod();
  }

  public void update(
      String eduNm,
      String eduTypeCd,
      LocalDate startDt,
      LocalDate endDt,
      BigDecimal eduHours,
      String resultCd) {
    this.eduNm = eduNm;
    this.eduTypeCd = eduTypeCd;
    this.startDt = startDt;
    this.endDt = endDt;
    this.eduHours = eduHours;
    this.resultCd = resultCd;
    validatePeriod();
  }

  private void validatePeriod() {
    if (endDt.isBefore(startDt)) {
      throw new IllegalArgumentException("교육 종료일이 시작일보다 앞설 수 없습니다: " + eduNm);
    }
  }

  public Long getId() {
    return id;
  }

  public Long getEmpId() {
    return empId;
  }

  public String getEduNm() {
    return eduNm;
  }

  public String getEduTypeCd() {
    return eduTypeCd;
  }

  public LocalDate getStartDt() {
    return startDt;
  }

  public LocalDate getEndDt() {
    return endDt;
  }

  public BigDecimal getEduHours() {
    return eduHours;
  }

  public String getResultCd() {
    return resultCd;
  }
}
