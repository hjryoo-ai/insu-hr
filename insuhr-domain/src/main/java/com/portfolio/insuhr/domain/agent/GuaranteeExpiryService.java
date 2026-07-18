package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.eligibility.AgentEligibilityReconciler;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재정보증 만료 물질화 (설계서 8 v2.0, {@code guaranteeExpiryJob}이 감싼다).
 *
 * <p><b>로스터에서 상태를 직접 바꾸는 유일한 잡</b>의 도메인 알맹이다. 나머지 잡은 적재(알림·발견)이거나 이미 reconciler를 타는데, 이 잡은 {@code
 * TB_FIN_GUARANTEE.STATUS_CD}를 {@code ACTIVE→EXPIRED}로 쓴다 — 자격 데이터 변경이므로 <b>반드시 reconcile을 유발</b>해야
 * 한다. 그래서 온라인 {@code AgentCredentialService.changeGuaranteeStatus}와 <b>같은 규약</b>을 도메인에 둔다: 엔티티 변경 →
 * {@link AgentEligibilityReconciler#reconcileAsOf}. 그 결과 §8의 "만료 처리 시 eligibility 재판정 트리거"가 별도 코드
 * 없이 Phase 5 배선으로 성립한다.
 *
 * <p><b>판정과의 순서 무관성</b>(설계서 8 v2.0): 모집자격 판정은 {@code STATUS_CD}가 아니라 {@link
 * FinGuarantee#isActiveOn}의 <b>기간 술어({@code END_DT >= asOf})</b>로 유효 보증을 센다. 그래서 이 잡이 EXPIRED를 아직 안
 * 찍었어도 {@code END_DT}가 지난 보증은 이미 합산에서 빠진다 — 이 잡과 {@code eligibilityRefreshJob} 중 무엇이 먼저 돌든 판정 결과가
 * 같고, 이 잡이 하루 죽어도 refresh가 모집 통제를 보장한다. 이 서비스가 하는 일은 그 판정과 <b>경계가 정확히 일치하는</b>({@code END_DT &lt;
 * asOf}) 상태 물질화 + 조회·리포트가 {@code STATUS_CD}로 보는 세계의 정합화다.
 */
@Service
public class GuaranteeExpiryService {

  private final FinGuaranteeRepository guaranteeRepository;
  private final AgentEligibilityReconciler reconciler;

  public GuaranteeExpiryService(
      FinGuaranteeRepository guaranteeRepository, AgentEligibilityReconciler reconciler) {
    this.guaranteeRepository = guaranteeRepository;
    this.reconciler = reconciler;
  }

  /**
   * 기준일에 만료가 지난 ACTIVE 보증 하나를 EXPIRED로 물질화하고 해당 설계사를 재판정한다.
   *
   * <p>경계는 판정과 일치: {@code END_DT < asOf}일 때만 만료다({@code END_DT == asOf}는 당일까지 유효 — {@code
   * isActiveOn} 경계 포함과 대칭). 이미 EXPIRED/CANCELED거나 아직 유효면 아무것도 하지 않고 {@code false} — 같은 {@code
   * targetDate} 재실행이 멱등하다.
   *
   * @return 실제로 만료 처리했으면 {@code true}
   */
  @Transactional
  public boolean expireGuarantee(Long guaranteeId, LocalDate asOf) {
    FinGuarantee guarantee =
        guaranteeRepository
            .findById(guaranteeId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        AgentErrorCode.NOT_FOUND, "재정보증을 찾을 수 없습니다: " + guaranteeId));

    if (guarantee.getStatus() != GuaranteeStatus.ACTIVE || !guarantee.getEndDt().isBefore(asOf)) {
      return false; // 이미 만료됐거나 아직 유효(경계 당일 포함) — 멱등 no-op
    }

    guarantee.changeStatus(GuaranteeStatus.EXPIRED);
    reconciler.reconcileAsOf(guarantee.getAgentId(), asOf);
    return true;
  }
}
