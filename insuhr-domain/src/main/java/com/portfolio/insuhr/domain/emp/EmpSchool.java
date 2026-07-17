package com.portfolio.insuhr.domain.emp;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** 인사기록카드 — 학력 (설계서 6.5). */
@Entity
@Table(name = "TB_EMP_SCHOOL")
public class EmpSchool extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "SCHOOL_ID")
  private Long id;

  @Column(name = "EMP_ID", nullable = false)
  private Long empId;

  @Column(name = "SCHOOL_NM", nullable = false, length = 100)
  private String schoolNm;

  @Column(name = "MAJOR_NM", length = 100)
  private String majorNm;

  @Column(name = "DEGREE_CD", nullable = false, length = 30)
  private String degreeCd;

  @Column(name = "GRAD_DT")
  private LocalDate gradDt;

  @Column(name = "GRAD_STATUS_CD", nullable = false, length = 30)
  private String gradStatusCd;

  protected EmpSchool() {}

  public EmpSchool(
      Long empId,
      String schoolNm,
      String majorNm,
      String degreeCd,
      LocalDate gradDt,
      String gradStatusCd) {
    this.empId = empId;
    this.schoolNm = schoolNm;
    this.majorNm = majorNm;
    this.degreeCd = degreeCd;
    this.gradDt = gradDt;
    this.gradStatusCd = gradStatusCd;
  }

  public void update(
      String schoolNm, String majorNm, String degreeCd, LocalDate gradDt, String gradStatusCd) {
    this.schoolNm = schoolNm;
    this.majorNm = majorNm;
    this.degreeCd = degreeCd;
    this.gradDt = gradDt;
    this.gradStatusCd = gradStatusCd;
  }

  public Long getId() {
    return id;
  }

  public Long getEmpId() {
    return empId;
  }

  public String getSchoolNm() {
    return schoolNm;
  }

  public String getMajorNm() {
    return majorNm;
  }

  public String getDegreeCd() {
    return degreeCd;
  }

  public LocalDate getGradDt() {
    return gradDt;
  }

  public String getGradStatusCd() {
    return gradStatusCd;
  }
}
