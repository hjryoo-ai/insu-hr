package com.portfolio.insuhr.domain.emp;

import com.portfolio.insuhr.common.exception.BusinessException;
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

/**
 * 인사발령 (설계서 5.5, 6.5).
 *
 * <p><b>이 행은 변경분이 아니라 발령 후 전체 상태를 담는다</b> (설계서 6.6의 "이력 행은 항상 전체 스냅샷" 전제). 덕분에 스냅샷 조회가 "행 하나 고르기"로
 * 끝난다 — 최초 발령부터 delta를 재생할 필요가 없다.
 *
 * <p><b>반영 여부는 이 엔티티가 갖지 않는다.</b> {@code APPLIED_YN} 같은 플래그를 두면 스냅샷과 어긋날 수 있는 두 번째 진실이 된다. 반영됐는지는
 * {@code CONFIRMED && appointDt <= 오늘}로 파생되며, 그 판정은 기준일을 아는 쪽(도메인 서비스)이 한다.
 */
@Entity
@Table(name = "TB_EMP_APPOINT")
public class Appointment extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "APPOINT_ID")
  private Long id;

  @Column(name = "EMP_ID", nullable = false)
  private Long empId;

  @Column(name = "APPOINT_TYPE_CD", nullable = false, length = 30)
  private String appointTypeCd;

  @Column(name = "APPOINT_DT", nullable = false)
  private LocalDate appointDt;

  @Column(name = "ORG_ID", nullable = false)
  private Long orgId;

  @Column(name = "JOB_GRADE_CD", length = 30)
  private String jobGradeCd;

  /** 직책. null은 "미지정"이 아니라 <b>보임 직책 없음</b>이다 — 발령 후 전체 상태를 담기 때문이다. */
  @Column(name = "JOB_TITLE_CD", length = 30)
  private String jobTitleCd;

  @Column(name = "EMP_STATUS_CD", nullable = false, length = 30)
  private String empStatusCd;

  @Column(name = "DOC_STATUS_CD", nullable = false, length = 30)
  private String docStatusCd;

  @Column(name = "RSN_TXT", length = 400)
  private String rsnTxt;

  protected Appointment() {}

  private Appointment(
      Long empId,
      AppointType type,
      LocalDate appointDt,
      Long orgId,
      String jobGradeCd,
      String jobTitleCd,
      EmpStatus status,
      String rsnTxt) {
    this.empId = empId;
    this.appointTypeCd = type.name();
    this.appointDt = appointDt;
    this.orgId = orgId;
    this.jobGradeCd = jobGradeCd;
    this.jobTitleCd = jobTitleCd;
    this.empStatusCd = status.name();
    this.docStatusCd = AppointDocStatus.DRAFT.name();
    this.rsnTxt = rsnTxt;
  }

  /**
   * 발령 기안.
   *
   * <p>재직상태는 인자로 받지 않는다 — 발령유형에서 파생하거나(입사/휴직/복직/퇴직) 발령일 시점의 직전 상태를 물려받는다(승진/전보/겸직/파견). 그래야 "승진 발령인데
   * 상태는 퇴직" 같은 조합이 애초에 만들어지지 않는다.
   *
   * @param baseStatus 발령일 시점의 직전 재직상태. 유형이 상태를 강제하지 않을 때 쓰인다
   */
  public static Appointment draft(
      Long empId,
      AppointType type,
      LocalDate appointDt,
      Long orgId,
      String jobGradeCd,
      String jobTitleCd,
      EmpStatus baseStatus,
      String rsnTxt) {
    EmpStatus status = type.resultingStatus().orElse(baseStatus);
    return new Appointment(empId, type, appointDt, orgId, jobGradeCd, jobTitleCd, status, rsnTxt);
  }

  /**
   * 발령 확정.
   *
   * <p>확정은 스냅샷을 직접 건드리지 않는다. 스냅샷은 확정된 발령들의 함수이므로(설계서 5.5), 이 행의 상태를 CONFIRMED로 바꾸는 것만으로 함수의 입력이
   * 바뀐다. 물질화는 {@code AppointmentApplyService}가 한다.
   */
  public void confirm() {
    if (!isDraft()) {
      throw new BusinessException(
          EmpErrorCode.APPOINT_NOT_DRAFT, "기안 상태의 발령만 확정할 수 있습니다. 현재 상태=" + docStatusCd);
    }
    this.docStatusCd = AppointDocStatus.CONFIRMED.name();
  }

  /**
   * 발령 취소 (설계서 5.5 v1.4).
   *
   * <p><b>이미 반영된 발령은 취소할 수 없다.</b> 반영된 과거를 지우면 이력과 스냅샷이 어긋나고, 그 발령을 근거로 이미 나간 Outbox 이벤트가 수신계에 남은 채
   * 원인만 사라진다. 되돌리려면 정정 발령(원래 값으로 되돌리는 새 발령)을 낸다.
   *
   * <p>취소 가능한 것은 ① 기안 ② 확정됐지만 발령일이 미래인 예약분뿐이다.
   *
   * @param today 업무 기준일. 시스템 날짜가 아니라 주입된 {@code Clock}에서 온다
   */
  public void cancel(LocalDate today) {
    if (isCanceled()) {
      throw new BusinessException(EmpErrorCode.APPOINT_ALREADY_CANCELED);
    }
    if (isAppliedOn(today)) {
      throw new BusinessException(
          EmpErrorCode.APPOINT_ALREADY_APPLIED,
          "이미 반영된 발령은 취소할 수 없습니다. 정정 발령으로 되돌리십시오. 발령일=" + appointDt);
    }
    this.docStatusCd = AppointDocStatus.CANCELED.name();
  }

  /**
   * 기준일 스냅샷에 반영되는 발령인가 (설계서 5.5).
   *
   * <p>별도 플래그가 아니라 파생값이다 — 배치가 아직 돌지 않았어도 정의가 흔들리지 않는다. 배치는 이 함수의 결과를 물질화할 뿐이다.
   */
  public boolean isAppliedOn(LocalDate asOf) {
    return isConfirmed() && !appointDt.isAfter(asOf);
  }

  public boolean isDraft() {
    return AppointDocStatus.DRAFT.name().equals(docStatusCd);
  }

  public boolean isConfirmed() {
    return AppointDocStatus.CONFIRMED.name().equals(docStatusCd);
  }

  public boolean isCanceled() {
    return AppointDocStatus.CANCELED.name().equals(docStatusCd);
  }

  /** 이벤트·이력에 실을 전체 스냅샷 (설계서 9.3 — 민감정보 금지). */
  public Map<String, Object> toSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("appointId", id);
    snapshot.put("empId", empId);
    snapshot.put("appointTypeCd", appointTypeCd);
    snapshot.put("appointDt", appointDt.toString());
    snapshot.put("orgId", orgId);
    snapshot.put("jobGradeCd", jobGradeCd);
    snapshot.put("jobTitleCd", jobTitleCd);
    snapshot.put("empStatusCd", empStatusCd);
    snapshot.put("docStatusCd", docStatusCd);
    return snapshot;
  }

  public Long getId() {
    return id;
  }

  public Long getEmpId() {
    return empId;
  }

  public AppointType getType() {
    return AppointType.valueOf(appointTypeCd);
  }

  public LocalDate getAppointDt() {
    return appointDt;
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

  public EmpStatus getStatus() {
    return EmpStatus.valueOf(empStatusCd);
  }

  public AppointDocStatus getDocStatus() {
    return AppointDocStatus.valueOf(docStatusCd);
  }

  public String getRsnTxt() {
    return rsnTxt;
  }
}
