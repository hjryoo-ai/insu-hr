package com.portfolio.insuhr.domain.emp;

import java.time.LocalDate;

/**
 * 특정 기준일의 임직원 상태 (설계서 5.5 v1.4).
 *
 * <p>발령 스냅샷 함수의 결과값이다:
 *
 * <pre>
 *   스냅샷(직원, 기준일 D) = CONFIRMED 이고 APPOINT_DT &lt;= D 인 발령 중
 *                            (APPOINT_DT, APPOINT_ID) 가 가장 큰 행
 * </pre>
 *
 * <p>이 값이 {@code TB_EMP}의 비정규화 컬럼으로 물질화된다. 물질화는 저장이지 계산이 아니다 — 계산의 원천은 항상 {@code TB_EMP_APPOINT}다.
 *
 * @param resignDt 퇴직일. 스냅샷 발령이 RESIGN일 때만 채워진다. 재입사(새 HIRE 발령)하면 다시 null이 되므로 별도로 지울 필요가 없다
 */
public record EmpSnapshot(
    Long orgId,
    String jobGradeCd,
    String jobTitleCd,
    EmpStatus status,
    LocalDate resignDt,
    Long sourceAppointId) {

  /** 발령 1건이 만드는 스냅샷. */
  public static EmpSnapshot of(Appointment appointment) {
    return new EmpSnapshot(
        appointment.getOrgId(),
        appointment.getJobGradeCd(),
        appointment.getJobTitleCd(),
        appointment.getStatus(),
        appointment.getType() == AppointType.RESIGN ? appointment.getAppointDt() : null,
        appointment.getId());
  }
}
