package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.common.error.ErrorDetail;
import java.util.List;

/**
 * 위촉 요건검증 SPI (설계서 5.3 v1.5).
 *
 * <p>위촉(CANDIDATE→PENDING_ASSOC)의 사전조건은 "유효 판매자격 1개 이상 + 등록교육 이수 + 유효 재정보증"인데(설계서 5.3 전이표), 그 입력이
 * 되는 테이블(TB_AGENT_LICENSE/EDU/FIN_GUARANTEE)은 <b>Phase 5</b> 소관이다. 그래서 Phase 4는 요건검증을 이 인터페이스로 분리하고
 * 상태머신이 이를 호출만 한다.
 *
 * <p>Phase 4에는 {@link AlwaysSatisfiedRequirementChecker}(스텁)가 주입돼 happy path가 성립한다. Phase 5가 {@code
 * RecruitEligibilityService} 기반 실판정으로 <b>서비스 코드 수정 없이</b> 교체한다 — {@code IntegrationRecorder}와 같은
 * 패턴이다. 이 분리 때문에 12장 시나리오 1이 1a(Phase 4: 상태머신의 422 성형)/1b(Phase 5: 실제 규칙)로 갈린다.
 *
 * <p>반환값이 {@link com.portfolio.insuhr.common.error.ErrorDetail} 목록인 이유: 위촉 요건 미충족은 예외 하나로 뭉개지 않고
 * <b>사유 배열을 담은 422</b>로 나가야 하기 때문이다(설계서 7.3). 빈 목록이면 충족이다.
 */
public interface RecruitmentRequirementChecker {

  /**
   * 위촉 요건을 검사한다.
   *
   * @return 미충족 사유 목록. <b>비어 있으면 충족</b>. 각 항목이 422 응답의 {@code details[]} 한 건이 된다
   */
  List<ErrorDetail> check(Long agentId);
}
