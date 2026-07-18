package com.portfolio.insuhr.domain.agent;

import java.util.Set;

/**
 * 판매자격 종목 (설계서 부록 A: LICENSE_TYPE, 5.4).
 *
 * <p>모집자격 판정이 <b>종목별</b>로 산출되므로, 각 종목은 어느 협회 등록이 있어야 모집 가능한지를 함께 안다(설계서 5.4). 제3보험은 생·손보 어느 협회 등록이든
 * 되고, 변액은 생보 계열이라 생명보험협회 등록 + 변액 판매자격을 요구한다.
 */
public enum LicenseType {
  /** 생명보험 */
  LIFE(Set.of(Association.LIFE_ASSOC)),
  /** 손해보험 */
  NONLIFE(Set.of(Association.NONLIFE_ASSOC)),
  /** 제3보험 — 생·손보 어느 협회 등록이든 가능 */
  THIRD(Set.of(Association.LIFE_ASSOC, Association.NONLIFE_ASSOC)),
  /** 변액보험 — 생보 계열 */
  VARIABLE(Set.of(Association.LIFE_ASSOC));

  private final Set<Association> acceptableAssocs;

  LicenseType(Set<Association> acceptableAssocs) {
    this.acceptableAssocs = acceptableAssocs;
  }

  /** 이 종목을 모집하려면 등록돼 있어야 하는 협회들 — 이 중 <b>하나라도</b> 등록이면 협회 게이트 통과. */
  public Set<Association> acceptableAssocs() {
    return acceptableAssocs;
  }

  public boolean isVariable() {
    return this == VARIABLE;
  }
}
