package com.portfolio.insuhr.domain.emp;

import java.util.Optional;

/**
 * 발령유형 (설계서 부록 A: APPOINT_TYPE, 5.5).
 *
 * <p>각 유형은 <b>발령 후 재직상태를 강제하는지</b>를 함께 안다. 발령 행의 {@code EMP_STATUS_CD}는 사용자 입력이 아니라 여기서 파생된다 — "승진
 * 발령을 냈는데 재직상태가 퇴직으로 들어가는" 조합을 입력으로 받지 않기 위함이다.
 *
 * <p>{@link #resultingStatus()}가 비어 있는 유형(승진·전보·겸직·파견)은 재직상태를 바꾸지 않는다. 그 경우 발령일 시점의 직전 상태를 그대로
 * 물려받는다.
 */
public enum AppointType {
  /** 입사 */
  HIRE(EmpStatus.ACTIVE),
  /** 승진 — 재직상태는 그대로 */
  PROMOTION(null),
  /** 전보 — 재직상태는 그대로 */
  TRANSFER(null),
  /** 겸직 — 재직상태는 그대로 */
  CONCURRENT(null),
  /** 휴직 */
  LEAVE(EmpStatus.ON_LEAVE),
  /** 복직 */
  RETURN(EmpStatus.ACTIVE),
  /** 파견 — 재직상태는 그대로 */
  DISPATCH(null),
  /** 퇴직 */
  RESIGN(EmpStatus.RESIGNED);

  private final EmpStatus resultingStatus;

  AppointType(EmpStatus resultingStatus) {
    this.resultingStatus = resultingStatus;
  }

  /**
   * 이 유형이 강제하는 발령 후 재직상태.
   *
   * @return 상태를 바꾸지 않는 유형이면 {@link Optional#empty()}
   */
  public Optional<EmpStatus> resultingStatus() {
    return Optional.ofNullable(resultingStatus);
  }
}
