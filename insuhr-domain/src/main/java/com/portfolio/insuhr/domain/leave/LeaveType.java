package com.portfolio.insuhr.domain.leave;

/**
 * 휴가유형 (설계서 부록 A: LEAVE_TYPE, 6.5).
 *
 * <p><b>연차 차감 여부를 여기서 안다.</b> 이 규칙을 공통코드 ATTR 슬롯에 두지 않은 이유: ATTR1~3은 외부 시스템 코드 매핑용이고(설계서 9.6),
 * "연차에서 깎이는가"는 매핑이 아니라 업무 규칙이다. 규칙은 도메인에 산다(CLAUDE.md 계층 규칙).
 */
public enum LeaveType {
  /** 연차 — 잔여일수에서 차감된다 */
  ANNUAL(true),
  /** 병가 */
  SICK(false),
  /** 경조사 */
  CONGRAT(false),
  /** 공가 */
  OFFICIAL(false),
  /** 무급휴가 */
  UNPAID(false);

  private final boolean deductsAnnual;

  LeaveType(boolean deductsAnnual) {
    this.deductsAnnual = deductsAnnual;
  }

  /** 이 휴가가 연차 잔여일수에서 차감되는가. */
  public boolean deductsAnnual() {
    return deductsAnnual;
  }
}
