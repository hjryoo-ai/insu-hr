package com.portfolio.insuhr.domain.eligibility;

import com.portfolio.insuhr.common.error.ErrorDetail;
import com.portfolio.insuhr.domain.agent.AgentAppointHistRepository;
import com.portfolio.insuhr.domain.agent.AgentEduRepository;
import com.portfolio.insuhr.domain.agent.AgentLicense;
import com.portfolio.insuhr.domain.agent.AgentLicenseRepository;
import com.portfolio.insuhr.domain.agent.EduType;
import com.portfolio.insuhr.domain.agent.FinGuarantee;
import com.portfolio.insuhr.domain.agent.FinGuaranteeRepository;
import com.portfolio.insuhr.domain.agent.RecruitmentRequirementChecker;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 위촉 요건검증 실판정 (설계서 5.3, 5.4 v1.6) — Phase 4의 스텁을 대체한다.
 *
 * <p>위촉(CANDIDATE→PENDING_ASSOC) 사전조건: <b>유효한 판매자격 1개 이상 + 등록교육 이수 + 유효한 재정보증</b>(합산 기준). 미충족은 사유
 * 배열로 반환돼 상태머신이 422로 성형한다(시나리오 1a/1b).
 *
 * <p><b>등록교육 신선도</b>는 정책값 {@code REG_EDU_REUSE_ON_REAPPOINT}가 정한다(설계서 5.4 v1.6). 기본 `Y`면 과거 이수를
 * 재사용하고, `N`이면 가장 최근 재위촉일 이후 이수한 등록교육만 유효하다 — 하드코딩하지 않는 정책 지점이다.
 *
 * <p>이 클래스가 {@code AlwaysSatisfiedRequirementChecker}를 대체한다. 그 스텁은 <b>소스에서 삭제</b>됐다 — 통제를 끄는 스텁이라
 * 살아남으면 무자격 위촉이 통과하기 때문이다(설계서 13.2 v1.6).
 */
@Component
public class EligibilityRequirementChecker implements RecruitmentRequirementChecker {

  private final AgentLicenseRepository licenseRepository;
  private final AgentEduRepository eduRepository;
  private final FinGuaranteeRepository guaranteeRepository;
  private final AgentAppointHistRepository histRepository;
  private final PolicyConfigService policyConfigService;
  private final Clock clock;

  public EligibilityRequirementChecker(
      AgentLicenseRepository licenseRepository,
      AgentEduRepository eduRepository,
      FinGuaranteeRepository guaranteeRepository,
      AgentAppointHistRepository histRepository,
      PolicyConfigService policyConfigService,
      Clock clock) {
    this.licenseRepository = licenseRepository;
    this.eduRepository = eduRepository;
    this.guaranteeRepository = guaranteeRepository;
    this.histRepository = histRepository;
    this.policyConfigService = policyConfigService;
    this.clock = clock;
  }

  @Override
  public List<ErrorDetail> check(Long agentId) {
    LocalDate today = LocalDate.now(clock);
    List<ErrorDetail> unmet = new ArrayList<>();

    boolean hasValidLicense =
        licenseRepository.findByAgentId(agentId).stream().anyMatch(AgentLicense::isValid);
    if (!hasValidLicense) {
      unmet.add(ErrorDetail.of("license", "NO_VALID_LICENSE", "유효한 판매자격이 없습니다."));
    }

    if (!hasRegEducation(agentId, today)) {
      unmet.add(ErrorDetail.of("regEducation", "REG_EDU_NOT_COMPLETED", "등록교육 이수 이력이 없습니다."));
    }

    BigDecimal minAmt = policyConfigService.getDecimal(PolicyKey.MIN_GRNT_AMT, today);
    BigDecimal guaranteedSum =
        guaranteeRepository.findByAgentId(agentId).stream()
            .filter(g -> g.isActiveOn(today))
            .map(FinGuarantee::getGrntAmt)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (guaranteedSum.compareTo(minAmt) < 0) {
      unmet.add(
          ErrorDetail.of(
              "finGuarantee",
              guaranteedSum.signum() == 0 ? "NO_ACTIVE_GUARANTEE" : "GUARANTEE_INSUFFICIENT",
              "유효 재정보증이 최소보증금액에 미달합니다."));
    }

    return unmet;
  }

  /** 등록교육 이수 여부 — 재위촉 신선도 정책({@code REG_EDU_REUSE_ON_REAPPOINT}) 적용. */
  private boolean hasRegEducation(Long agentId, LocalDate today) {
    boolean reuse =
        "Y"
            .equalsIgnoreCase(
                policyConfigService.getString(PolicyKey.REG_EDU_REUSE_ON_REAPPOINT, today));
    if (reuse) {
      return eduRepository.existsByAgentIdAndEduTypeCd(agentId, EduType.REG.name());
    }
    Optional<LocalDate> reappointDate = histRepository.findLatestReappointDate(agentId);
    return reappointDate
        .map(
            since ->
                eduRepository.existsByAgentIdAndEduTypeCdAndCompleteDtGreaterThanEqual(
                    agentId, EduType.REG.name(), since))
        .orElseGet(() -> eduRepository.existsByAgentIdAndEduTypeCd(agentId, EduType.REG.name()));
  }
}
