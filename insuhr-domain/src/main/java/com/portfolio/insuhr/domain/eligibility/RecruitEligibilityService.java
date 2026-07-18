package com.portfolio.insuhr.domain.eligibility;

import com.portfolio.insuhr.common.error.ErrorDetail;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.agent.Agent;
import com.portfolio.insuhr.domain.agent.AgentEdu;
import com.portfolio.insuhr.domain.agent.AgentEduRepository;
import com.portfolio.insuhr.domain.agent.AgentErrorCode;
import com.portfolio.insuhr.domain.agent.AgentLicense;
import com.portfolio.insuhr.domain.agent.AgentLicenseRepository;
import com.portfolio.insuhr.domain.agent.AgentRepository;
import com.portfolio.insuhr.domain.agent.AgentSanctionRepository;
import com.portfolio.insuhr.domain.agent.AssocReg;
import com.portfolio.insuhr.domain.agent.AssocRegRepository;
import com.portfolio.insuhr.domain.agent.Association;
import com.portfolio.insuhr.domain.agent.EduType;
import com.portfolio.insuhr.domain.agent.FinGuarantee;
import com.portfolio.insuhr.domain.agent.FinGuaranteeRepository;
import com.portfolio.insuhr.domain.agent.LicenseType;
import com.portfolio.insuhr.domain.eligibility.EligibilityResult.LineVerdict;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모집자격 판정 — <b>부수효과 없는 순수 함수</b> (설계서 5.4 v1.6).
 *
 * <p>{@link #evaluate}는 데이터·기준일만으로 결과가 정해지고 상태 전이를 절대 하지 않는다. 전이(ACTIVE↔SUSPENDED)는 {@code
 * AgentEligibilityReconciler}가 이 결과를 받아 상태머신 관문으로 집행한다. 이 분리 덕에 Phase 7 배치가 판정 함수를 그대로 재사용하고,
 * {@code asOf} 파라미터로 앵커 Clock 경계 테스트(6.2)가 판정에도 적용된다.
 *
 * <p><b>보수교육 null 기저선</b>(5.4 v1.6): 이수 이력이 없으면 기한 = {@code LAST_APPOINT_DT + 주기}. null을 "요건 없음"으로
 * 읽으면 영원히 통과, "미충족"으로 읽으면 위촉 직후 전원 정지 — 둘 다 버그라 기저선을 명시한다.
 *
 * <p><b>경계는 inclusive</b>: 보수교육/보증 {@code >= 오늘}, 제재는 엔티티의 {@code isBlockingOn}(양끝 포함).
 */
@Service
public class RecruitEligibilityService {

  private final AgentRepository agentRepository;
  private final AgentLicenseRepository licenseRepository;
  private final AgentEduRepository eduRepository;
  private final FinGuaranteeRepository guaranteeRepository;
  private final AgentSanctionRepository sanctionRepository;
  private final AssocRegRepository assocRegRepository;
  private final PolicyConfigService policyConfigService;

  public RecruitEligibilityService(
      AgentRepository agentRepository,
      AgentLicenseRepository licenseRepository,
      AgentEduRepository eduRepository,
      FinGuaranteeRepository guaranteeRepository,
      AgentSanctionRepository sanctionRepository,
      AssocRegRepository assocRegRepository,
      PolicyConfigService policyConfigService) {
    this.agentRepository = agentRepository;
    this.licenseRepository = licenseRepository;
    this.eduRepository = eduRepository;
    this.guaranteeRepository = guaranteeRepository;
    this.sanctionRepository = sanctionRepository;
    this.assocRegRepository = assocRegRepository;
    this.policyConfigService = policyConfigService;
  }

  /**
   * 기준일 시점의 모집자격을 판정한다 — 부수효과 없음.
   *
   * @param asOf 판정 기준일 (배치는 targetDate, 실시간은 오늘). 경계 판정이 전부 이 값 기준이다
   */
  @Transactional(readOnly = true)
  public EligibilityResult evaluate(Long agentId, LocalDate asOf) {
    Agent agent = require(agentId);
    List<ErrorDetail> common = new ArrayList<>();

    // ── 공통 게이트 ──
    boolean eduPass = continuingEduPass(agent, asOf);
    if (!eduPass) {
      common.add(ErrorDetail.of("continuingEducation", "NEXT_DUE_DT_PASSED", "보수교육 이수기한이 지났습니다."));
    }

    BigDecimal minAmt = policyConfigService.getDecimal(PolicyKey.MIN_GRNT_AMT, asOf);
    BigDecimal guaranteedSum =
        guaranteeRepository.findByAgentId(agentId).stream()
            .filter(g -> g.isActiveOn(asOf))
            .map(FinGuarantee::getGrntAmt)
            .reduce(BigDecimal.ZERO, BigDecimal::add); // 복수 보증은 합산 (5.4 v1.6)
    boolean grntPass = guaranteedSum.compareTo(minAmt) >= 0;
    if (!grntPass) {
      common.add(
          ErrorDetail.of(
              "finGuarantee",
              guaranteedSum.signum() == 0 ? "NO_ACTIVE_GUARANTEE" : "GUARANTEE_INSUFFICIENT",
              "유효 재정보증 합산이 최소보증금액에 미달합니다."));
    }

    boolean blocked =
        sanctionRepository.findByAgentId(agentId).stream().anyMatch(s -> s.isBlockingOn(asOf));
    if (blocked) {
      common.add(ErrorDetail.of("sanction", "RECRUIT_BLOCKED", "진행 중인 모집정지 제재가 있습니다."));
    }

    boolean commonGate = eduPass && grntPass && !blocked;

    // ── 종목별 게이트 ──
    Set<Association> registeredAssocs =
        assocRegRepository.findByAgentId(agentId).stream()
            .filter(AssocReg::isRegistered)
            .map(AssocReg::getAssoc)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Association.class)));
    Set<LicenseType> validLicenses =
        licenseRepository.findByAgentId(agentId).stream()
            .filter(AgentLicense::isValid)
            .map(AgentLicense::getType)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(LicenseType.class)));

    List<LineVerdict> lines = new ArrayList<>();
    boolean anyLineAvailable = false;
    for (LicenseType line : LicenseType.values()) {
      List<ErrorDetail> lineReasons = new ArrayList<>();
      boolean hasLicense = validLicenses.contains(line);
      if (!hasLicense) {
        lineReasons.add(ErrorDetail.of("license", "NO_VALID_LICENSE", line + " 판매자격이 유효하지 않습니다."));
      }
      boolean assocOk = line.acceptableAssocs().stream().anyMatch(registeredAssocs::contains);
      if (!assocOk) {
        lineReasons.add(ErrorDetail.of("assocReg", "ASSOC_NOT_REGISTERED", line + " 협회 등록이 없습니다."));
      }
      boolean lineAvailable = hasLicense && assocOk; // 종목 게이트 (공통 게이트와 독립)
      anyLineAvailable = anyLineAvailable || lineAvailable;
      lines.add(new LineVerdict(line, commonGate && lineAvailable, lineReasons));
    }

    boolean substantive = commonGate && anyLineAvailable;
    return new EligibilityResult(substantive, lines, common);
  }

  /**
   * 보수교육 게이트. 최신 CONTINUING 이수의 {@code NEXT_DUE_DT}, 이수 이력이 없으면 기저선({@code LAST_APPOINT_DT + 주기}).
   * {@code NEXT_DUE_DT >= asOf}면 통과(경계 당일 포함).
   */
  private boolean continuingEduPass(Agent agent, LocalDate asOf) {
    LocalDate due =
        eduRepository
            .findFirstByAgentIdAndEduTypeCdOrderByNextDueDtDesc(
                agent.getId(), EduType.CONTINUING.name())
            .map(AgentEdu::getNextDueDt)
            .orElseGet(() -> baselineDue(agent, asOf));
    return due != null && !due.isBefore(asOf);
  }

  private LocalDate baselineDue(Agent agent, LocalDate asOf) {
    if (agent.getLastAppointDt() == null) {
      return null; // 위촉 전이면 보수교육 기저선이 없다 — ACTIVE 설계사에겐 항상 LAST_APPOINT_DT가 있다
    }
    int cycleMonths = policyConfigService.getInt(PolicyKey.CONT_EDU_CYCLE_MONTHS, asOf);
    return agent.getLastAppointDt().plusMonths(cycleMonths);
  }

  private Agent require(Long agentId) {
    return agentRepository
        .findById(agentId)
        .orElseThrow(
            () -> new BusinessException(AgentErrorCode.NOT_FOUND, "설계사를 찾을 수 없습니다: " + agentId));
  }
}
