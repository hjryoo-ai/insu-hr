package com.portfolio.insuhr.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.emp.EmployeeService;
import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.AgentTestConfig;
import com.portfolio.insuhr.api.support.ConfigurableRequirementChecker;
import com.portfolio.insuhr.api.support.MutableClock;
import com.portfolio.insuhr.api.support.RecordingIntegrationRecorder;
import com.portfolio.insuhr.api.support.StubRequirementCheckerConfig;
import com.portfolio.insuhr.api.support.TestClockConfig;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.common.error.ErrorDetail;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.agent.Agent;
import com.portfolio.insuhr.domain.agent.AgentAppointHistRepository;
import com.portfolio.insuhr.domain.agent.AgentContractRepository;
import com.portfolio.insuhr.domain.agent.AgentRepository;
import com.portfolio.insuhr.domain.agent.AgentStatus;
import com.portfolio.insuhr.domain.agent.Channel;
import com.portfolio.insuhr.domain.agent.TermReason;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.emp.EmpType;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 설계사 위촉 상태머신 통합 테스트 (설계서 5.3, 시나리오 1a·4).
 *
 * <p>실제 Oracle + Flyway 위에서 상태머신을 굴린다. {@link MutableClock}으로 냉각기간 경계를 옮겨 가며 확인하고, {@link
 * RecordingIntegrationRecorder}로 전이마다 이벤트가 정확히 1건 나가는지 본다. 요건검증은 {@link
 * ConfigurableRequirementChecker}로 좌우한다(시나리오 1a).
 */
@Import({TestClockConfig.class, AgentTestConfig.class, StubRequirementCheckerConfig.class})
class AgentLifecycleIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired AgentService agentService;
  @Autowired AgentRepository agentRepository;
  @Autowired AgentAppointHistRepository histRepository;
  @Autowired AgentContractRepository contractRepository;
  @Autowired OrgRepository orgRepository;
  @Autowired EmployeeService employeeService;
  @Autowired EmpRepository empRepository;
  @Autowired MutableClock clock;
  @Autowired RecordingIntegrationRecorder recorder;
  @Autowired ConfigurableRequirementChecker requirementChecker;

  @BeforeEach
  void resetDoubles() {
    requirementChecker.satisfy();
    recorder.clear();
    clock.setDate(LocalDate.of(2026, 3, 1));
  }

  private Long newOrg() {
    int n = SEQ.getAndIncrement();
    return orgRepository
        .save(
            Org.create(
                TestSeq.orgCd(), "조직" + n, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1)))
        .getId();
  }

  private NewPerson newPerson() {
    int n = SEQ.getAndIncrement();
    return new NewPerson(
        "설계사" + n, TestSeq.rrn(), LocalDate.of(1988, 5, 5), Gender.F, "01099998888", null, "KR");
  }

  private AgentService.RegisterResult register(Long orgId) {
    return agentService.registerCandidate(
        newPerson(), new AgentService.RegisterCommand(Channel.FC, orgId, null));
  }

  private void appoint(Long agentId, LocalDate appointDt) {
    agentService.appoint(
        agentId,
        appointDt,
        new AgentService.ContractCommand("FC_STD", "2026-1", "004", "110-1234-5678", "홍길동"));
  }

  private Agent reload(Long agentId) {
    return agentRepository.findById(agentId).orElseThrow();
  }

  @Test
  @DisplayName("상태머신 전이를 통과한다: 후보→위촉→활성→정지→해촉")
  void fullLifecycle() {
    // 요건검증은 스텁(StubRequirementCheckerConfig)이 통과시킨다 — 여기서는 전이 규칙만 본다.
    // 정지해제(resume)는 실제 모집자격 게이트가 걸리므로 자격 테스트(AgentEligibilityIntegrationTest)에서 다룬다.
    Long org = newOrg();
    AgentService.RegisterResult reg = register(org);
    assertThat(reg.agentCd()).matches("A\\d{8}");
    assertThat(reload(reg.agentId()).getStatus()).isEqualTo(AgentStatus.CANDIDATE);

    appoint(reg.agentId(), LocalDate.of(2026, 3, 1));
    assertThat(reload(reg.agentId()).getStatus()).isEqualTo(AgentStatus.PENDING_ASSOC);
    assertThat(contractRepository.findByAgentIdOrderByValidFromDtDesc(reg.agentId())).hasSize(1);

    agentService.registerAssociation(reg.agentId(), LocalDate.of(2026, 3, 5), "L-2026-001");
    assertThat(reload(reg.agentId()).getStatus()).isEqualTo(AgentStatus.ACTIVE);

    agentService.suspend(reg.agentId(), LocalDate.of(2026, 4, 1), "EDU_OVERDUE", "보수교육 미이수");
    assertThat(reload(reg.agentId()).getStatus()).isEqualTo(AgentStatus.SUSPENDED);

    // SUSPENDED → TERMINATED (해촉은 정지 상태에서도 가능).
    agentService.terminate(reg.agentId(), LocalDate.of(2026, 5, 1), TermReason.SELF, "자진 해촉");
    assertThat(reload(reg.agentId()).getStatus()).isEqualTo(AgentStatus.TERMINATED);
  }

  @Test
  @DisplayName("시나리오 1a: 요건 미충족이면 위촉이 422 + 사유 배열로 거부되고 상태·계약이 그대로다")
  void scenario1a_unmetRequirementsYield422WithReasons() {
    Long org = newOrg();
    AgentService.RegisterResult reg = register(org);

    requirementChecker.failWith(
        ErrorDetail.of("license", "NO_VALID_LICENSE", "유효한 판매자격이 없습니다."),
        ErrorDetail.of("finGuarantee", "GUARANTEE_EXPIRED", "재정보증이 만료되었습니다."));

    assertThatThrownBy(() -> appoint(reg.agentId(), LocalDate.of(2026, 3, 1)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.errorCode().code()).isEqualTo("AGT-4221");
              assertThat(e.errorCode().httpStatus()).isEqualTo(422);
              assertThat(e.details()).hasSize(2);
              assertThat(e.details())
                  .extracting(ErrorDetail::reason)
                  .containsExactly("NO_VALID_LICENSE", "GUARANTEE_EXPIRED");
            });

    // 요건 미충족이면 전이도 계약 생성도 일어나지 않는다.
    assertThat(reload(reg.agentId()).getStatus()).isEqualTo(AgentStatus.CANDIDATE);
    assertThat(contractRepository.findByAgentIdOrderByValidFromDtDesc(reg.agentId())).isEmpty();
  }

  @Test
  @DisplayName("전이마다 recorder가 정확히 1회, 카탈로그의 eventType으로 호출된다 (배선 누락 방지)")
  void recorderFiresOncePerTransitionWithCatalogEventType() {
    Long org = newOrg();

    recorder.clear();
    AgentService.RegisterResult reg = register(org);
    assertThat(recorder.events()).hasSize(1);
    assertThat(recorder.events().get(0).eventType()).isEqualTo("agent.candidate.registered");

    recorder.clear();
    appoint(reg.agentId(), LocalDate.of(2026, 3, 1));
    assertThat(recorder.events()).hasSize(1);
    assertThat(recorder.events().get(0).eventType()).isEqualTo("agent.status.changed");

    recorder.clear();
    agentService.registerAssociation(reg.agentId(), LocalDate.of(2026, 3, 5), "L-1");
    assertThat(recorder.events()).hasSize(1);
    assertThat(recorder.events().get(0).eventType()).isEqualTo("agent.appointed");

    recorder.clear();
    agentService.terminate(reg.agentId(), LocalDate.of(2026, 3, 9), TermReason.COMPANY, "회사 해촉");
    assertThat(recorder.events()).hasSize(1);
    assertThat(recorder.events().get(0).eventType()).isEqualTo("agent.terminated");
    // 이벤트 페이로드에 민감정보 없음 — 업무키(agentCd)만.
    assertThat(recorder.events().get(0).businessKey()).isEqualTo(reg.agentCd());
  }

  @Test
  @DisplayName("시나리오 4: 냉각기간 전 재위촉은 거부, 경과 후에는 같은 AGENT_ID로 재위촉된다")
  void scenario4_reappointCooldown() {
    clock.setDate(LocalDate.of(2025, 6, 1));
    Long org = newOrg();
    AgentService.RegisterResult reg = register(org);
    appoint(reg.agentId(), LocalDate.of(2025, 6, 1));
    agentService.registerAssociation(reg.agentId(), LocalDate.of(2025, 6, 2), "L-1");
    agentService.terminate(reg.agentId(), LocalDate.of(2025, 6, 1), TermReason.SELF, "자진");

    // 해촉 6/1 + 6개월 = 12/1. 그 전날(11/30)에는 냉각기간 미경과로 거부.
    clock.setDate(LocalDate.of(2025, 11, 30));
    assertThatThrownBy(() -> agentService.reappoint(reg.agentId(), "재위촉 시도"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("재위촉 제한기간");

    // 냉각기간 경과 후엔 같은 AGENT_ID·AGENT_CD로 재위촉(CANDIDATE 복귀).
    clock.setDate(LocalDate.of(2025, 12, 1));
    agentService.reappoint(reg.agentId(), "냉각기간 경과");

    Agent reappointed = reload(reg.agentId());
    assertThat(reappointed.getId()).isEqualTo(reg.agentId());
    assertThat(reappointed.getAgentCd()).isEqualTo(reg.agentCd());
    assertThat(reappointed.getStatus()).isEqualTo(AgentStatus.CANDIDATE);
    assertThat(reappointed.getTerminateDt()).isNull();
    assertThat(reappointed.getTerminateRsnCd()).isNull();
    assertThat(reappointed.getFirstAppointDt())
        .as("최초 위촉일은 보존")
        .isEqualTo(LocalDate.of(2025, 6, 1));
  }

  @Test
  @DisplayName("시나리오 4: 징계해촉은 냉각기간과 무관하게 재위촉이 영구 거부된다")
  void scenario4_disciplineTerminationForbidsReappointForever() {
    clock.setDate(LocalDate.of(2020, 1, 1));
    Long org = newOrg();
    AgentService.RegisterResult reg = register(org);
    appoint(reg.agentId(), LocalDate.of(2020, 1, 1));
    agentService.registerAssociation(reg.agentId(), LocalDate.of(2020, 1, 2), "L-1");
    agentService.terminate(
        reg.agentId(), LocalDate.of(2020, 1, 1), TermReason.DISCIPLINE, "모집질서 문란");

    // 6년 뒤(냉각기간 훨씬 초과)에도 징계해촉은 거부.
    clock.setDate(LocalDate.of(2026, 3, 1));
    assertThatThrownBy(() -> agentService.reappoint(reg.agentId(), "재위촉 시도"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("재위촉이 금지된 해촉사유");
  }

  @Test
  @DisplayName("재위촉 후에도 전이 이력은 누적되어 과거 해촉이 남는다 (마스터=현재, 과거는 이력에만)")
  void reappointPreservesHistory() {
    clock.setDate(LocalDate.of(2025, 1, 1));
    Long org = newOrg();
    AgentService.RegisterResult reg = register(org); // hist: null→CANDIDATE
    appoint(reg.agentId(), LocalDate.of(2025, 1, 1)); // →PENDING_ASSOC
    agentService.registerAssociation(reg.agentId(), LocalDate.of(2025, 1, 2), "L-1"); // →ACTIVE
    agentService.terminate(
        reg.agentId(), LocalDate.of(2025, 1, 3), TermReason.SELF, "자진"); // →TERMINATED
    clock.setDate(LocalDate.of(2025, 8, 1));
    agentService.reappoint(reg.agentId(), "재위촉"); // →CANDIDATE

    // 마스터에서 해촉일은 지워졌지만, 이력에는 TERMINATED 전이가 그대로 남아 있다.
    assertThat(reload(reg.agentId()).getTerminateDt()).isNull();
    assertThat(histRepository.findByAgentIdOrderByIdAsc(reg.agentId()))
        .extracting(h -> h.getToStatus().name())
        .containsExactly("CANDIDATE", "PENDING_ASSOC", "ACTIVE", "TERMINATED", "CANDIDATE");
  }

  @Test
  @DisplayName("같은 주민번호로 임직원과 설계사 이중 역할 — PERSON은 1건으로 유지된다 (시나리오 8 관련)")
  void dualRoleReusesSinglePerson() {
    Long org = newOrg();
    NewPerson person = newPerson();

    EmployeeService.HireResult hired =
        employeeService.hire(
            person,
            new EmployeeService.HireCommand(
                EmpType.REGULAR, org, "STAFF", null, LocalDate.of(2026, 1, 2)));

    AgentService.RegisterResult agent =
        agentService.registerCandidate(
            person, new AgentService.RegisterCommand(Channel.FC, org, null));

    // 같은 인물이 두 역할을 갖되 PERSON 행은 하나다.
    assertThat(agent.personId()).isEqualTo(hired.personId());
    assertThat(empRepository.findByPersonId(hired.personId())).isPresent();
    assertThat(agentRepository.findByPersonId(hired.personId())).isPresent();
  }

  @Test
  @DisplayName("이미 설계사인 인물을 다시 후보등록하면 거부된다 (인물당 설계사 0..1)")
  void duplicateAgentRoleRejected() {
    Long org = newOrg();
    NewPerson person = newPerson();
    agentService.registerCandidate(person, new AgentService.RegisterCommand(Channel.FC, org, null));

    assertThatThrownBy(
            () ->
                agentService.registerCandidate(
                    person, new AgentService.RegisterCommand(Channel.TC, org, null)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("이미 설계사로 등록된 인물");
  }

  // Phase 5: resume 판정 게이트의 @Disabled 빚은 해제됐다 — AgentService.resume이 실제 모집자격을 검사하고,
  // AgentEligibilityIntegrationTest가 "제재 진행 중이면 정지해제 거부, 제재 종료 후 허용"을 검증한다(설계서 5.4 v1.6).
}
