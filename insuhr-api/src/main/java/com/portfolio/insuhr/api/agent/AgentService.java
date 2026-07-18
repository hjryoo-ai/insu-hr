package com.portfolio.insuhr.api.agent;

import com.portfolio.insuhr.api.person.PersonService;
import com.portfolio.insuhr.common.crypto.AesGcmCipher;
import com.portfolio.insuhr.common.error.ErrorDetail;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.agent.Agent;
import com.portfolio.insuhr.domain.agent.AgentCodeGenerator;
import com.portfolio.insuhr.domain.agent.AgentContract;
import com.portfolio.insuhr.domain.agent.AgentContractRepository;
import com.portfolio.insuhr.domain.agent.AgentErrorCode;
import com.portfolio.insuhr.domain.agent.AgentGenealogyQueryDao;
import com.portfolio.insuhr.domain.agent.AgentLifecycleService;
import com.portfolio.insuhr.domain.agent.AgentRepository;
import com.portfolio.insuhr.domain.agent.Channel;
import com.portfolio.insuhr.domain.agent.TermReason;
import com.portfolio.insuhr.domain.eligibility.EligibilityResult;
import com.portfolio.insuhr.domain.eligibility.RecruitEligibilityService;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgErrorCode;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설계사 유스케이스 (설계서 7.2 AGT).
 *
 * <p>상태 전이 규칙 자체는 {@link AgentLifecycleService}(도메인)가 갖는다. 이 애플리케이션 서비스는 유스케이스 경계에서 상태머신 밖의 일을 엮는다
 * — 인물 생성/연결(후보등록), 조직 유효성, 위촉계약 생성(계좌 암호화), 도입자 순환 방어, 계보 조립.
 */
@Service
public class AgentService {

  private final PersonService personService;
  private final AgentLifecycleService lifecycleService;
  private final AgentRepository agentRepository;
  private final AgentContractRepository contractRepository;
  private final AgentGenealogyQueryDao genealogyQueryDao;
  private final AgentCodeGenerator agentCodeGenerator;
  private final OrgRepository orgRepository;
  private final AesGcmCipher cipher;
  private final RecruitEligibilityService eligibilityService;

  public AgentService(
      PersonService personService,
      AgentLifecycleService lifecycleService,
      AgentRepository agentRepository,
      AgentContractRepository contractRepository,
      AgentGenealogyQueryDao genealogyQueryDao,
      AgentCodeGenerator agentCodeGenerator,
      OrgRepository orgRepository,
      AesGcmCipher cipher,
      RecruitEligibilityService eligibilityService) {
    this.personService = personService;
    this.lifecycleService = lifecycleService;
    this.agentRepository = agentRepository;
    this.contractRepository = contractRepository;
    this.genealogyQueryDao = genealogyQueryDao;
    this.agentCodeGenerator = agentCodeGenerator;
    this.orgRepository = orgRepository;
    this.cipher = cipher;
    this.eligibilityService = eligibilityService;
  }

  /**
   * 후보 등록 (설계서 7.2 {@code POST /agents/candidates}, 부록 B 1단계).
   *
   * <p>인물은 이미 있으면 재사용된다(설계서 5.2) — 직원이었거나 설계사였다가 해촉된 사람이 이 경로로 들어온다. 같은 사람이 이미 설계사 역할이면 막는다(인물당 설계사
   * 0..1, 실제 방어선은 {@code UX_AGENT_PERSON}).
   */
  @Transactional
  public RegisterResult registerCandidate(NewPerson person, RegisterCommand command) {
    Long personId = personService.register(person).personId();

    agentRepository
        .findByPersonId(personId)
        .ifPresent(
            existing -> {
              throw new BusinessException(
                  AgentErrorCode.ALREADY_AGENT, "이미 설계사로 등록된 인물입니다. agentId=" + existing.getId());
            });

    Org org = requireActiveOrg(command.orgId());
    Long recruiterAgentId = resolveRecruiter(command.recruiterAgentCd());

    Agent agent =
        lifecycleService.registerCandidate(
            personId, agentCodeGenerator.next(), command.channel(), org.getId(), recruiterAgentId);

    return new RegisterResult(agent.getId(), agent.getAgentCd(), personId);
  }

  /**
   * 위촉 실행 (설계서 7.2 {@code POST /agents/{id}/appoint}, 부록 B 5단계).
   *
   * <p>요건검증 통과 시 PENDING_ASSOC 전이(상태머신) + 위촉계약 생성. 요건 미충족이면 상태머신이 422를 던져 계약도 만들어지지 않는다(시나리오 1a).
   * 지급계좌는 암호문으로만 저장한다(설계서 6.8).
   */
  @Transactional
  public void appoint(Long agentId, LocalDate appointDt, ContractCommand contract) {
    lifecycleService.appoint(agentId, appointDt);

    contractRepository.save(
        AgentContract.create(
            agentId,
            contract.contractTypeCd(),
            appointDt,
            contract.commRuleVer(),
            contract.bankCd(),
            cipher.encrypt(contract.account()),
            contract.accountHolderNm(),
            appointDt));
  }

  /**
   * 협회 등록번호 입력 → ACTIVE (설계서 7.2, 부록 B 6단계).
   *
   * <p><b>여기서 재판정하지 않는다.</b> 이건 위촉 워크플로 전이(협회 등록번호 수신)이지 자격 쓰기가 아니다 — 자격을 바꾸는 것은 {@code
   * AgentCredentialService}의 자격·교육·보증·제재·종목협회 등록이고, 그쪽만 reconciler를 탄다(설계서 5.4 v1.6). 이 전이 직후 재판정을
   * 걸면 아직 종목 협회 등록(TB_ASSOC_REG)이 안 된 시점이라 갓 ACTIVE가 된 설계사를 곧바로 자동 SUSPENDED로 떨어뜨린다. {@code
   * RECRUIT_ELIG_YN}은 이후 자격 쓰기나 배치가 갱신한다.
   */
  @Transactional
  public void registerAssociation(Long agentId, LocalDate eventDt, String assocRegNo) {
    lifecycleService.registerAssociation(agentId, eventDt, assocRegNo);
  }

  /** 모집정지 (설계서 7.2 {@code POST /agents/{id}/suspend}). */
  @Transactional
  public void suspend(Long agentId, LocalDate eventDt, String rsnCd, String rsnTxt) {
    lifecycleService.suspend(agentId, eventDt, rsnCd, rsnTxt);
  }

  /**
   * 정지해제 (설계서 7.2 {@code POST /agents/{id}/resume}).
   *
   * <p><b>모집자격 판정 게이트 (Phase 5, 설계서 5.4 v1.6).</b> Phase 4에서 판정 없이 열려 있던 수동 resume에 게이트를 붙인다 — 실질
   * 자격을 회복하지 못한 설계사(제재 진행 중 등)를 수동으로 활성화할 수 없다. 미회복이면 사유 배열을 담은 422다. 자동 resume(reconciler 경로)은 이미
   * 판정을 통과한 뒤라 이 게이트를 우회하는 것이 정상이다 — reconciler는 도메인 전이({@code lifecycleService.resume})를 직접 부른다.
   */
  @Transactional
  public void resume(Long agentId, LocalDate eventDt, String rsnTxt) {
    EligibilityResult result = eligibilityService.evaluate(agentId, lifecycleService.today());
    if (!result.substantiveEligible()) {
      List<ErrorDetail> reasons =
          result.commonReasons().isEmpty()
              ? List.of(ErrorDetail.of("recruitment", "NO_ELIGIBLE_LINE", "모집 가능한 종목이 없습니다."))
              : result.commonReasons();
      throw new BusinessException(AgentErrorCode.RESUME_NOT_ELIGIBLE, reasons);
    }
    lifecycleService.resume(agentId, eventDt, rsnTxt);
  }

  /** 해촉 (설계서 7.2 {@code POST /agents/{id}/terminate}, 부록 B 9단계). */
  @Transactional
  public void terminate(Long agentId, LocalDate eventDt, TermReason reason, String rsnTxt) {
    lifecycleService.terminate(agentId, eventDt, reason, rsnTxt);
  }

  /** 재위촉 (설계서 7.2 {@code POST /agents/{id}/reappoint}, 부록 B 10단계, 시나리오 4). */
  @Transactional
  public void reappoint(Long agentId, String rsnTxt) {
    lifecycleService.reappoint(agentId, rsnTxt);
  }

  /** 소속 이동 (설계서 7.2 {@code PUT /agents/{id}/transfer}). 옮겨갈 조직도 개설 상태여야 한다. */
  @Transactional
  public void transfer(Long agentId, Long newOrgId, LocalDate eventDt, String rsnTxt) {
    Org org = requireActiveOrg(newOrgId);
    lifecycleService.transfer(agentId, org.getId(), eventDt, rsnTxt);
  }

  /** 설계사 상세 (설계서 7.2 {@code GET /agents/{id}}). */
  @Transactional(readOnly = true)
  public Agent get(Long agentId) {
    return requireAgent(agentId);
  }

  /** 모집자격 실시간 판정 (설계서 7.2 {@code GET /agents/{id}/eligibility}). 종목별 결과 + 사유. */
  @Transactional(readOnly = true)
  public EligibilityResult eligibility(Long agentId) {
    requireAgent(agentId);
    return eligibilityService.evaluate(agentId, lifecycleService.today());
  }

  /** 도입 계보 트리 (설계서 7.2 {@code GET /agents/{id}/genealogy}). */
  @Transactional(readOnly = true)
  public GenealogyNode genealogy(Long agentId) {
    requireAgent(agentId);
    List<AgentGenealogyQueryDao.GenealogyRow> rows = genealogyQueryDao.findGenealogy(agentId);
    return assemble(rows, agentId);
  }

  /**
   * 도입자 순환 방어 (설계서 5.3 v1.5).
   *
   * <p>{@code recruiter}를 {@code target}의 도입자로 지정하려는데, recruiter가 이미 target의 하위 계보에 있으면 A→B→A 순환이
   * 되어 계보 조회 CONNECT BY가 죽는다(NOCYCLE로 죽진 않지만 계보가 오염된다). 조직 이관의 {@code guardAgainstCycle}과 같은 발상이다.
   *
   * <p>Phase 4는 도입자 재지정 엔드포인트가 없어 이 경로가 API로 도달 불가하지만, 방어를 지금 넣어 두고(재지정 추가 시 즉시 유효) 테스트로 검증한다.
   */
  public void assertRecruiterAssignable(Long targetAgentId, Long recruiterAgentId) {
    if (recruiterAgentId == null) {
      return;
    }
    if (genealogyQueryDao.findDescendantAgentIds(targetAgentId).contains(recruiterAgentId)) {
      throw new BusinessException(
          AgentErrorCode.RECRUITER_CYCLE,
          "도입자 지정이 계보에 순환을 만듭니다. target=" + targetAgentId + ", recruiter=" + recruiterAgentId);
    }
  }

  private Long resolveRecruiter(String recruiterAgentCd) {
    if (recruiterAgentCd == null || recruiterAgentCd.isBlank()) {
      return null;
    }
    return agentRepository
        .findByAgentCd(recruiterAgentCd)
        .orElseThrow(
            () ->
                new BusinessException(
                    AgentErrorCode.NOT_FOUND, "도입자 설계사를 찾을 수 없습니다: " + recruiterAgentCd))
        .getId();
  }

  private Org requireActiveOrg(Long orgId) {
    Org org =
        orgRepository
            .findById(orgId)
            .orElseThrow(
                () -> new BusinessException(OrgErrorCode.NOT_FOUND, "조직을 찾을 수 없습니다: " + orgId));
    if (!org.isActiveOn(lifecycleService.today())) {
      throw new BusinessException(
          AgentErrorCode.ORG_CLOSED, "폐지되었거나 개설 전인 조직입니다. 조직=" + org.getOrgCd());
    }
    return org;
  }

  private Agent requireAgent(Long agentId) {
    return agentRepository
        .findById(agentId)
        .orElseThrow(
            () -> new BusinessException(AgentErrorCode.NOT_FOUND, "설계사를 찾을 수 없습니다: " + agentId));
  }

  /** 평면 계보 행을 트리로 조립한다. 루트는 조회 기준 설계사(depth 1). */
  private GenealogyNode assemble(List<AgentGenealogyQueryDao.GenealogyRow> rows, Long rootAgentId) {
    Map<Long, GenealogyNode> byId = new LinkedHashMap<>();
    for (AgentGenealogyQueryDao.GenealogyRow row : rows) {
      byId.put(
          row.agentId(),
          new GenealogyNode(
              row.agentId(), row.agentCd(), row.statusCd(), row.orgId(), new ArrayList<>()));
    }
    GenealogyNode root = byId.get(rootAgentId);
    for (AgentGenealogyQueryDao.GenealogyRow row : rows) {
      if (row.agentId().equals(rootAgentId)) {
        continue; // 루트의 도입자는 트리 밖이다
      }
      GenealogyNode parent = byId.get(row.recruiterAgentId());
      if (parent != null) {
        parent.children().add(byId.get(row.agentId()));
      }
    }
    return root;
  }

  /** 후보등록 명령. 인물(NewPerson)과 분리 — 인물은 재사용될 수 있어 후보등록 파라미터가 아니다. */
  public record RegisterCommand(Channel channel, Long orgId, String recruiterAgentCd) {}

  public record RegisterResult(Long agentId, String agentCd, Long personId) {}

  /** 위촉계약 명령. {@code account}는 평문 — 서비스가 암호화해 저장한다(로그 금지). */
  public record ContractCommand(
      String contractTypeCd,
      String commRuleVer,
      String bankCd,
      String account,
      String accountHolderNm) {

    @Override
    public String toString() {
      return "ContractCommand[contractTypeCd=" + contractTypeCd + ", account=***]";
    }
  }

  /** 도입 계보 트리 노드. */
  public record GenealogyNode(
      Long agentId, String agentCd, String statusCd, Long orgId, List<GenealogyNode> children) {}
}
