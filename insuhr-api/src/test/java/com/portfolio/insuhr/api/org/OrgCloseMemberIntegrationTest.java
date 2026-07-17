package com.portfolio.insuhr.api.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.agent.AgentService;
import com.portfolio.insuhr.api.emp.EmployeeService;
import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.agent.Channel;
import com.portfolio.insuhr.domain.agent.TermReason;
import com.portfolio.insuhr.domain.emp.AppointType;
import com.portfolio.insuhr.domain.emp.EmpType;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 조직 폐지 시 소속인원 검사 (설계서 7.2 — 소속인원 존재 시 409).
 *
 * <p>Phase 2에서 TB_EMP가 없어 비워 뒀던 빚을 Phase 3에서 갚는 자리다. TB_AGENT 검사는 Phase 4까지 남으므로, 그 절반은 아래
 * {@code @Disabled} 테스트로 실행 가능한 형태로 남긴다(설계서 13.2 v1.4) — 산문 메모보다 잊기 어렵다.
 */
class OrgCloseMemberIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired OrgService orgService;
  @Autowired EmployeeService employeeService;
  @Autowired AgentService agentService;
  @Autowired OrgRepository orgRepository;

  private String newOrgCd() {
    String cd = TestSeq.orgCd();
    orgRepository.save(
        Org.create(cd, "조직" + cd, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1)));
    return cd;
  }

  private Long orgIdOf(String cd) {
    return orgRepository.findByOrgCd(cd).orElseThrow().getId();
  }

  private void hireInto(Long orgId) {
    int n = SEQ.getAndIncrement();
    employeeService.hire(
        new NewPerson(
            "직원" + n, TestSeq.rrn(), LocalDate.of(1990, 1, 1), Gender.M, "01012341234", null, "KR"),
        new EmployeeService.HireCommand(
            EmpType.REGULAR, orgId, "STAFF", null, LocalDate.of(2026, 1, 2)));
  }

  @Test
  @DisplayName("재직 임직원이 소속된 조직은 폐지할 수 없다 (409)")
  void cannotCloseOrgWithActiveEmployee() {
    String cd = newOrgCd();
    hireInto(orgIdOf(cd));

    assertThatThrownBy(() -> orgService.close(cd, LocalDate.of(2026, 6, 30), "폐지 시도"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("소속 재직");
  }

  @Test
  @DisplayName("소속 임직원이 없는 조직은 폐지된다")
  void closesOrgWithNoMembers() {
    String cd = newOrgCd();
    assertThatCode(() -> orgService.close(cd, LocalDate.of(2026, 6, 30), "정상 폐지"))
        .doesNotThrowAnyException();
    assertThat(orgRepository.findByOrgCd(cd).orElseThrow().isClosed()).isTrue();
  }

  @Test
  @DisplayName("퇴직자만 남은 조직은 폐지할 수 있다 — 퇴직자의 소속은 이력적 사실이다")
  void closesOrgWithOnlyResignedEmployee() {
    String cd = newOrgCd();
    Long orgId = orgIdOf(cd);
    int n = SEQ.getAndIncrement();
    Long empId =
        employeeService
            .hire(
                new NewPerson(
                    "퇴직예정" + n,
                    TestSeq.rrn(),
                    LocalDate.of(1990, 1, 1),
                    Gender.M,
                    "01012341234",
                    null,
                    "KR"),
                new EmployeeService.HireCommand(
                    EmpType.REGULAR, orgId, "STAFF", null, LocalDate.of(2026, 1, 2)))
            .empId();

    // 퇴직 발령을 과거(오늘 이전) 날짜로 내 즉시 반영되게 한다.
    Long appointId =
        employeeService.draftAppointment(
            empId,
            new EmployeeService.AppointmentCommand(
                AppointType.RESIGN, LocalDate.of(2026, 1, 3), orgId, "STAFF", null, "퇴직"));
    employeeService.confirmAppointment(appointId);

    assertThatCode(() -> orgService.close(cd, LocalDate.of(2026, 6, 30), "퇴직자만 남음"))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("소속 현역 설계사가 있는 조직은 폐지할 수 없다 (Phase 4에 갚은 나머지 절반)")
  void cannotCloseOrgWithActiveAgent() {
    String cd = newOrgCd();
    registerAgentInto(orgIdOf(cd));

    assertThatThrownBy(() -> orgService.close(cd, LocalDate.of(2026, 6, 30), "폐지 시도"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("소속 현역 설계사");
  }

  @Test
  @DisplayName("해촉 설계사만 남은 조직은 폐지할 수 있다 — 해촉자의 소속은 이력적 사실이다 (임직원 퇴직자와 대칭)")
  void closesOrgWithOnlyTerminatedAgent() {
    String cd = newOrgCd();
    Long orgId = orgIdOf(cd);
    Long agentId = registerAgentInto(orgId);

    // 위촉 → 활성 → 해촉까지 몰고 간다.
    agentService.appoint(
        agentId,
        LocalDate.of(2026, 1, 2),
        new AgentService.ContractCommand("FC_STD", "2026-1", null, null, null));
    agentService.registerAssociation(agentId, LocalDate.of(2026, 1, 3), "L-1");
    agentService.terminate(agentId, LocalDate.of(2026, 1, 10), TermReason.SELF, "자진");

    assertThatCode(() -> orgService.close(cd, LocalDate.of(2026, 6, 30), "해촉자만 남음"))
        .doesNotThrowAnyException();
  }

  private Long registerAgentInto(Long orgId) {
    int n = SEQ.getAndIncrement();
    return agentService
        .registerCandidate(
            new NewPerson(
                "설계사" + n,
                TestSeq.rrn(),
                LocalDate.of(1988, 5, 5),
                Gender.F,
                "01055556666",
                null,
                "KR"),
            new AgentService.RegisterCommand(Channel.FC, orgId, null))
        .agentId();
  }
}
