package com.portfolio.insuhr.domain.emp;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 임직원 (설계서 6.4).
 *
 * <p>인물(TB_PERSON) 아래 붙는 <b>역할</b>이다 (설계서 5.2). 주민번호는 여기 없고 인물이 갖는다.
 *
 * <p><b>소속·직급·직책·재직상태는 이 엔티티가 스스로 바꾸지 않는다.</b> 그 값들은 발령의 함수이고(설계서 5.5), 이 엔티티는 계산 결과를 받아 적는 비정규화
 * 스냅샷일 뿐이다. 그래서 {@code changeOrg()} 같은 메서드가 없고 {@link #applySnapshot(EmpSnapshot)} 하나만 있다 — 발령을 거치지
 * 않고 소속을 바꾸는 경로를 만들지 않기 위함이다.
 */
@Entity
@Table(name = "TB_EMP")
public class Emp extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "EMP_ID")
  private Long id;

  @Column(name = "PERSON_ID", nullable = false)
  private Long personId;

  @Column(name = "EMP_NO", nullable = false, length = 10)
  private String empNo;

  @Column(name = "EMP_TYPE_CD", nullable = false, length = 30)
  private String empTypeCd;

  @Column(name = "ORG_ID", nullable = false)
  private Long orgId;

  @Column(name = "JOB_GRADE_CD", length = 30)
  private String jobGradeCd;

  @Column(name = "JOB_TITLE_CD", length = 30)
  private String jobTitleCd;

  @Column(name = "HIRE_DT", nullable = false)
  private LocalDate hireDt;

  @Column(name = "RESIGN_DT")
  private LocalDate resignDt;

  @Column(name = "EMP_STATUS_CD", nullable = false, length = 30)
  private String empStatusCd;

  protected Emp() {}

  private Emp(
      Long personId,
      String empNo,
      EmpType empType,
      Long orgId,
      String jobGradeCd,
      String jobTitleCd,
      LocalDate hireDt) {
    this.personId = personId;
    this.empNo = empNo;
    this.empTypeCd = empType.name();
    this.orgId = orgId;
    this.jobGradeCd = jobGradeCd;
    this.jobTitleCd = jobTitleCd;
    this.hireDt = hireDt;
    this.empStatusCd = EmpStatus.ACTIVE.name();
  }

  /**
   * 입사.
   *
   * <p>입사 직후 스냅샷은 입사 발령이 확정되면 그 발령에서 다시 계산된다. 여기서 채우는 초기값은 그 계산이 오기 전까지의 값이며, 입사 발령과 같은 값이라 결과는 같다.
   *
   * @param empNo 사번. 시퀀스 채번이라 도메인이 만들 수 없다 — 호출부가 넘긴다 (설계서 6.4)
   */
  public static Emp hire(
      Long personId,
      String empNo,
      EmpType empType,
      Long orgId,
      String jobGradeCd,
      String jobTitleCd,
      LocalDate hireDt) {
    return new Emp(personId, empNo, empType, orgId, jobGradeCd, jobTitleCd, hireDt);
  }

  /**
   * 발령 스냅샷 반영 (설계서 5.5 v1.4).
   *
   * <p>증분 갱신이 아니라 <b>재계산 결과의 통째 대입</b>이다. 몇 번을 반영해도 같은 결과라 배치 재실행이 안전하다.
   *
   * @return 실제로 값이 바뀌었으면 true. 배치가 불필요한 이벤트를 발행하지 않도록 판단하는 데 쓴다
   */
  public boolean applySnapshot(EmpSnapshot snapshot) {
    if (!isChangedBy(snapshot)) {
      return false;
    }
    this.orgId = snapshot.orgId();
    this.jobGradeCd = snapshot.jobGradeCd();
    this.jobTitleCd = snapshot.jobTitleCd();
    this.empStatusCd = snapshot.status().name();
    this.resignDt = snapshot.resignDt();
    return true;
  }

  private boolean isChangedBy(EmpSnapshot snapshot) {
    return !Objects.equals(orgId, snapshot.orgId())
        || !Objects.equals(jobGradeCd, snapshot.jobGradeCd())
        || !Objects.equals(jobTitleCd, snapshot.jobTitleCd())
        || !Objects.equals(empStatusCd, snapshot.status().name())
        || !Objects.equals(resignDt, snapshot.resignDt());
  }

  public boolean isResigned() {
    return EmpStatus.RESIGNED.name().equals(empStatusCd);
  }

  /** 현재 스냅샷을 값으로. 발령 기안이 "직전 상태"를 물려받을 때 쓴다. */
  public EmpSnapshot currentSnapshot() {
    return new EmpSnapshot(orgId, jobGradeCd, jobTitleCd, getStatus(), resignDt, null);
  }

  /** 이벤트 페이로드 (설계서 9.3 — 민감정보 금지. 업무키와 코드만 싣는다). */
  public Map<String, Object> toSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("empId", id);
    snapshot.put("empNo", empNo);
    snapshot.put("personId", personId);
    snapshot.put("empTypeCd", empTypeCd);
    snapshot.put("orgId", orgId);
    snapshot.put("jobGradeCd", jobGradeCd);
    snapshot.put("jobTitleCd", jobTitleCd);
    snapshot.put("hireDt", hireDt.toString());
    snapshot.put("resignDt", resignDt == null ? null : resignDt.toString());
    snapshot.put("empStatusCd", empStatusCd);
    return snapshot;
  }

  public Long getId() {
    return id;
  }

  public Long getPersonId() {
    return personId;
  }

  public String getEmpNo() {
    return empNo;
  }

  public EmpType getEmpType() {
    return EmpType.valueOf(empTypeCd);
  }

  public Long getOrgId() {
    return orgId;
  }

  public String getJobGradeCd() {
    return jobGradeCd;
  }

  public String getJobTitleCd() {
    return jobTitleCd;
  }

  public LocalDate getHireDt() {
    return hireDt;
  }

  public LocalDate getResignDt() {
    return resignDt;
  }

  public EmpStatus getStatus() {
    return EmpStatus.valueOf(empStatusCd);
  }
}
