package com.portfolio.insuhr.domain.emp;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** 인사기록카드 — 경력 (설계서 6.5). 입사 전 타사 경력이다. */
@Entity
@Table(name = "TB_EMP_CAREER")
public class EmpCareer extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "CAREER_ID")
  private Long id;

  @Column(name = "EMP_ID", nullable = false)
  private Long empId;

  @Column(name = "COMPANY_NM", nullable = false, length = 100)
  private String companyNm;

  @Column(name = "DEPT_NM", length = 100)
  private String deptNm;

  @Column(name = "POSITION_NM", length = 50)
  private String positionNm;

  @Column(name = "JOIN_DT", nullable = false)
  private LocalDate joinDt;

  @Column(name = "LEAVE_DT")
  private LocalDate leaveDt;

  @Column(name = "JOB_DESC", length = 400)
  private String jobDesc;

  protected EmpCareer() {}

  public EmpCareer(
      Long empId,
      String companyNm,
      String deptNm,
      String positionNm,
      LocalDate joinDt,
      LocalDate leaveDt,
      String jobDesc) {
    this.empId = empId;
    this.companyNm = companyNm;
    this.deptNm = deptNm;
    this.positionNm = positionNm;
    this.joinDt = joinDt;
    this.leaveDt = leaveDt;
    this.jobDesc = jobDesc;
    validatePeriod();
  }

  public void update(
      String companyNm,
      String deptNm,
      String positionNm,
      LocalDate joinDt,
      LocalDate leaveDt,
      String jobDesc) {
    this.companyNm = companyNm;
    this.deptNm = deptNm;
    this.positionNm = positionNm;
    this.joinDt = joinDt;
    this.leaveDt = leaveDt;
    this.jobDesc = jobDesc;
    validatePeriod();
  }

  // CK_EMP_CAREER_PERIOD와 같은 규칙이다. DB 제약은 최후 방어선이고, 여기서 걸러야 사용자가
  // ORA-02290 대신 업무 메시지를 본다.
  private void validatePeriod() {
    if (leaveDt != null && leaveDt.isBefore(joinDt)) {
      throw new IllegalArgumentException("퇴사일이 입사일보다 앞설 수 없습니다: " + companyNm);
    }
  }

  public Long getId() {
    return id;
  }

  public Long getEmpId() {
    return empId;
  }

  public String getCompanyNm() {
    return companyNm;
  }

  public String getDeptNm() {
    return deptNm;
  }

  public String getPositionNm() {
    return positionNm;
  }

  public LocalDate getJoinDt() {
    return joinDt;
  }

  public LocalDate getLeaveDt() {
    return leaveDt;
  }

  public String getJobDesc() {
    return jobDesc;
  }
}
