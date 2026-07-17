package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.common.error.ErrorDetail;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Phase 4 위촉 요건검증 스텁 (설계서 5.3 v1.5).
 *
 * <p>항상 충족(빈 목록)을 반환한다 — Phase 4에는 판매자격·교육·재정보증 테이블이 없으므로 검사할 대상이 없다. 덕분에 Phase 4의 위촉 happy
 * path(CANDIDATE→PENDING_ASSOC)가 성립한다.
 *
 * <p><b>Phase 5 작업</b>: {@code RecruitEligibilityService} 기반 실판정 구현을 추가하고 <b>이 클래스를 삭제</b>한다({@code
 * NoOpIntegrationRecorder}와 같은 방식 — 조건부 등록이 아니라 삭제. 스텁이 조용히 살아남아 요건검증이 무력화되느니 빈 충돌로 기동이 실패해 즉시
 * 알아차리는 편이 낫다). 상태머신 서비스({@code AgentLifecycleService})는 이 교체에 영향받지 않는다.
 */
@Component
public class AlwaysSatisfiedRequirementChecker implements RecruitmentRequirementChecker {

  @Override
  public List<ErrorDetail> check(Long agentId) {
    return List.of();
  }
}
