package com.portfolio.insuhr.api.agent;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.agent.AgentEdu;
import com.portfolio.insuhr.domain.agent.AgentEduRepository;
import com.portfolio.insuhr.domain.agent.AgentErrorCode;
import com.portfolio.insuhr.domain.agent.AgentLicense;
import com.portfolio.insuhr.domain.agent.AgentLicenseRepository;
import com.portfolio.insuhr.domain.agent.AgentSanction;
import com.portfolio.insuhr.domain.agent.AgentSanctionRepository;
import com.portfolio.insuhr.domain.agent.AssocReg;
import com.portfolio.insuhr.domain.agent.AssocRegRepository;
import com.portfolio.insuhr.domain.agent.Association;
import com.portfolio.insuhr.domain.agent.EduPolicyRepository;
import com.portfolio.insuhr.domain.agent.EduType;
import com.portfolio.insuhr.domain.agent.FinGuarantee;
import com.portfolio.insuhr.domain.agent.FinGuaranteeRepository;
import com.portfolio.insuhr.domain.agent.GuaranteeStatus;
import com.portfolio.insuhr.domain.agent.LicenseStatus;
import com.portfolio.insuhr.domain.agent.LicenseType;
import com.portfolio.insuhr.domain.eligibility.AgentEligibilityReconciler;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설계사 자격·교육·보증·제재 쓰기 유스케이스 (설계서 7.2 AGT, 5.4).
 *
 * <p><b>모든 쓰기가 {@link AgentEligibilityReconciler}를 거친다</b>(설계서 5.4 v1.6). 자격·교육·보증·제재가 바뀌면 모집자격이 바뀔
 * 수 있으므로, 쓰기 → 재판정 → (필요시)자동 전이 + {@code RECRUIT_ELIG_YN} 갱신 + 종합 판정 변화 시 이벤트가 한 흐름으로 일어난다. 제재 등록 시
 * 자동 SUSPENDED(7.2)도 이 경로다 — 별도 코드가 없다.
 */
@Service
public class AgentCredentialService {

  private final AgentLicenseRepository licenseRepository;
  private final AgentEduRepository eduRepository;
  private final EduPolicyRepository eduPolicyRepository;
  private final FinGuaranteeRepository guaranteeRepository;
  private final AgentSanctionRepository sanctionRepository;
  private final AssocRegRepository assocRegRepository;
  private final AgentEligibilityReconciler reconciler;
  private final PolicyConfigService policyConfigService;

  public AgentCredentialService(
      AgentLicenseRepository licenseRepository,
      AgentEduRepository eduRepository,
      EduPolicyRepository eduPolicyRepository,
      FinGuaranteeRepository guaranteeRepository,
      AgentSanctionRepository sanctionRepository,
      AssocRegRepository assocRegRepository,
      AgentEligibilityReconciler reconciler,
      PolicyConfigService policyConfigService) {
    this.licenseRepository = licenseRepository;
    this.eduRepository = eduRepository;
    this.eduPolicyRepository = eduPolicyRepository;
    this.guaranteeRepository = guaranteeRepository;
    this.sanctionRepository = sanctionRepository;
    this.assocRegRepository = assocRegRepository;
    this.reconciler = reconciler;
    this.policyConfigService = policyConfigService;
  }

  /** 판매자격 등록/갱신 (설계서 7.2 {@code POST /agents/{id}/licenses}). */
  @Transactional
  public Long registerLicense(
      Long agentId,
      LicenseType type,
      String licenseNo,
      LocalDate examPassDt,
      LocalDate regDt,
      LicenseStatus status) {
    Long id =
        licenseRepository
            .save(AgentLicense.register(agentId, type, licenseNo, examPassDt, regDt, status))
            .getId();
    reconciler.reconcile(agentId);
    return id;
  }

  /** 판매자격 상태 변경 (정지/말소/복원). */
  @Transactional
  public void changeLicenseStatus(Long licenseId, LicenseStatus status) {
    AgentLicense license = require(licenseRepository.findById(licenseId), "판매자격", licenseId);
    license.changeStatus(status);
    reconciler.reconcile(license.getAgentId());
  }

  /**
   * 교육 이수 등록 (설계서 7.2 {@code POST /agents/{id}/educations}).
   *
   * <p>보수교육이면 {@code NEXT_DUE_DT = COMPLETE_DT + 주기}(정책값 {@code CONT_EDU_CYCLE_MONTHS})를 자동계산한다.
   * 이수시간이 정책 최소시간에 미달하면 거부한다.
   */
  @Transactional
  public Long registerEducation(
      Long agentId,
      EduType type,
      String eduNm,
      LocalDate completeDt,
      BigDecimal eduHours,
      String providerNm) {
    validateMinHours(type, eduHours);
    LocalDate nextDueDt = nextDueFor(type, completeDt);
    Long id =
        eduRepository
            .save(
                AgentEdu.complete(
                    agentId, type, eduNm, completeDt, eduHours, providerNm, nextDueDt))
            .getId();
    reconciler.reconcile(agentId);
    return id;
  }

  /** 재정보증 등록/갱신 (설계서 7.2 {@code POST /agents/{id}/guarantees}). 시나리오 3의 갱신 경로. */
  @Transactional
  public Long registerGuarantee(
      Long agentId,
      String grntTypeCd,
      BigDecimal grntAmt,
      String issuerNm,
      String policyNo,
      LocalDate startDt,
      LocalDate endDt,
      GuaranteeStatus status) {
    Long id =
        guaranteeRepository
            .save(
                FinGuarantee.register(
                    agentId, grntTypeCd, grntAmt, issuerNm, policyNo, startDt, endDt, status))
            .getId();
    reconciler.reconcile(agentId);
    return id;
  }

  @Transactional
  public void changeGuaranteeStatus(Long guaranteeId, GuaranteeStatus status) {
    FinGuarantee guarantee =
        require(guaranteeRepository.findById(guaranteeId), "재정보증", guaranteeId);
    guarantee.changeStatus(status);
    reconciler.reconcile(guarantee.getAgentId());
  }

  /**
   * 제재 등록 (설계서 7.2 {@code POST /agents/{id}/sanctions}).
   *
   * <p>{@code recruitBlock=true}이면 재판정에서 실질 자격이 깨져 reconciler가 ACTIVE→SUSPENDED로 자동 전이한다 — "제재 등록 시
   * 자동 SUSPENDED"(7.2)가 별도 코드 없이 이 경로로 성립한다.
   */
  @Transactional
  public Long imposeSanction(
      Long agentId,
      String sanctionOrgCd,
      String sanctionTypeCd,
      LocalDate startDt,
      LocalDate endDt,
      String rsnTxt,
      boolean recruitBlock) {
    Long id =
        sanctionRepository
            .save(
                AgentSanction.impose(
                    agentId, sanctionOrgCd, sanctionTypeCd, startDt, endDt, rsnTxt, recruitBlock))
            .getId();
    reconciler.reconcile(agentId);
    return id;
  }

  /** 제재 종료 (설계서 7.2 sanctions). 종료 후 재판정으로 자동 ACTIVE 복귀가 가능해진다. */
  @Transactional
  public void liftSanction(Long sanctionId, LocalDate endDt) {
    AgentSanction sanction = require(sanctionRepository.findById(sanctionId), "제재", sanctionId);
    sanction.lift(endDt);
    reconciler.reconcile(sanction.getAgentId());
  }

  /** 협회 등록 (설계서 7.2 assoc-registrations의 종목 협회 등록분). */
  @Transactional
  public Long registerAssoc(Long agentId, Association assoc, String assocRegNo, LocalDate regDt) {
    Long id = assocRegRepository.save(AssocReg.register(agentId, assoc, assocRegNo, regDt)).getId();
    reconciler.reconcile(agentId);
    return id;
  }

  /** 협회 말소. */
  @Transactional
  public void deregisterAssoc(Long assocRegId, LocalDate deregDt, String deregRsnCd) {
    AssocReg reg = require(assocRegRepository.findById(assocRegId), "협회등록", assocRegId);
    reg.deregister(deregDt, deregRsnCd);
    reconciler.reconcile(reg.getAgentId());
  }

  private void validateMinHours(EduType type, BigDecimal eduHours) {
    eduPolicyRepository
        .findFirstByEduTypeCdOrderByValidFromDtDesc(type.name())
        .ifPresent(
            policy -> {
              if (eduHours.compareTo(policy.getMinHours()) < 0) {
                throw new BusinessException(
                    AgentErrorCode.EDU_HOURS_INSUFFICIENT,
                    "이수시간이 최소 기준에 미달합니다. 최소=" + policy.getMinHours() + ", 이수=" + eduHours);
              }
            });
  }

  private LocalDate nextDueFor(EduType type, LocalDate completeDt) {
    if (type != EduType.CONTINUING) {
      return null; // 주기가 있는 것은 보수교육뿐 (설계서 5.4)
    }
    int cycleMonths = policyConfigService.getInt(PolicyKey.CONT_EDU_CYCLE_MONTHS, completeDt);
    return completeDt.plusMonths(cycleMonths);
  }

  private <T> T require(java.util.Optional<T> found, String label, Long id) {
    return found.orElseThrow(
        () -> new BusinessException(AgentErrorCode.NOT_FOUND, label + "을(를) 찾을 수 없습니다: " + id));
  }
}
