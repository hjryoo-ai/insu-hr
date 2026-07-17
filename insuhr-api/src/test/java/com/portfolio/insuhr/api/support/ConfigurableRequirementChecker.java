package com.portfolio.insuhr.api.support;

import com.portfolio.insuhr.common.error.ErrorDetail;
import com.portfolio.insuhr.domain.agent.RecruitmentRequirementChecker;
import java.util.List;

/**
 * 테스트가 위촉 요건 판정을 좌우할 수 있는 checker (시나리오 1a).
 *
 * <p>기본은 충족(빈 목록)이라 happy path가 성립한다. 시나리오 1a는 {@link #failWith}로 미충족 사유를 심어, 상태머신이 그 판정을 <b>422 +
 * 사유 배열</b>로 성형하는지 검증한다. Phase 5의 실판정({@code RecruitEligibilityService})이 할 일을 테스트가 대역으로 주입하는 것이다 —
 * 상태머신의 계약(미충족 → 422 성형)은 요건 규칙과 독립적으로 검증된다.
 */
public class ConfigurableRequirementChecker implements RecruitmentRequirementChecker {

  private volatile List<ErrorDetail> nextResult = List.of();

  @Override
  public List<ErrorDetail> check(Long agentId) {
    return nextResult;
  }

  /** 다음 검사부터 이 사유들로 미충족 판정한다. */
  public void failWith(ErrorDetail... failures) {
    this.nextResult = List.of(failures);
  }

  /** 충족(빈 목록)으로 되돌린다. */
  public void satisfy() {
    this.nextResult = List.of();
  }
}
