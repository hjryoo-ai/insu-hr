package com.portfolio.insuhr.domain.eligibility;

import com.portfolio.insuhr.common.error.ErrorDetail;
import com.portfolio.insuhr.domain.agent.LicenseType;
import java.util.List;

/**
 * 모집자격 판정 결과 (설계서 5.4). {@code RecruitEligibilityService.evaluate}의 반환값 — 부수효과 없는 계산의 산물이다.
 *
 * <p><b>{@code substantiveEligible}은 위촉상태를 제외한 실질 자격이다</b>(보수교육·재정보증·제재 공통 게이트 통과 AND 모집 가능 종목 ≥
 * 1). 상태를 여기서 빼는 이유는 순환을 피하기 위함이다 — reconciler가 이 값으로 ACTIVE↔SUSPENDED를 <b>결정</b>하는데, 판정이 상태에 의존하면
 * 닭-달걀이 된다. "지금 모집 가능한가"라는 최종 질문은 {@code substantiveEligible && status==ACTIVE}이고, 그 조합이 {@code
 * TB_AGENT.RECRUIT_ELIG_YN}이다(설계서 5.4 v1.6 종합 YN 정의).
 *
 * @param substantiveEligible 공통 게이트 통과 AND 모집 가능 종목 ≥ 1 (상태 제외)
 * @param lines 종목별 판정
 * @param commonReasons 공통 게이트 실패 사유 (전 종목에 공통으로 영향)
 */
public record EligibilityResult(
    boolean substantiveEligible, List<LineVerdict> lines, List<ErrorDetail> commonReasons) {

  public EligibilityResult {
    lines = List.copyOf(lines);
    commonReasons = List.copyOf(commonReasons);
  }

  /** 종목 하나의 판정. */
  public record LineVerdict(LicenseType line, boolean eligible, List<ErrorDetail> reasons) {
    public LineVerdict {
      reasons = List.copyOf(reasons);
    }
  }
}
