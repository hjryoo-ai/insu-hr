package com.portfolio.insuhr.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.api.agent.AgentService;
import com.portfolio.insuhr.api.emp.EmployeeService;
import com.portfolio.insuhr.api.org.OrgService;
import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.domain.agent.Channel;
import com.portfolio.insuhr.domain.emp.EmpType;
import com.portfolio.insuhr.domain.integration.IfChangeLog;
import com.portfolio.insuhr.domain.integration.IfChangeLogRepository;
import com.portfolio.insuhr.domain.integration.IfOutbox;
import com.portfolio.insuhr.domain.integration.IfOutboxRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 이벤트 페이로드 민감정보 부재 (설계서 9.3, 10장 통제의 일부 / Phase 6 완료 기준).
 *
 * <p>이벤트는 사외로 나가는 데이터다. "마스킹된 이름과 업무키만 싣는다"는 9.3 규칙을 <b>테스트로 강제</b>한다 — 이 인물의 주민번호·휴대폰이 어떤 이벤트
 * 페이로드나 변경로그 스냅샷에도 원문으로 등장하지 않음을 단언한다. 마스킹·복호화 통제가 이벤트 경로에서 새면 여기서 잡힌다.
 */
class PayloadNoSensitiveDataTest extends AbstractIntegrationTest {

  @Autowired OrgService orgService;
  @Autowired EmployeeService employeeService;
  @Autowired AgentService agentService;
  @Autowired IfOutboxRepository outboxRepository;
  @Autowired IfChangeLogRepository changeLogRepository;

  @Test
  @DisplayName("입사·후보등록·조직 이벤트 페이로드에 주민번호·휴대폰 원문이 없다")
  void payloadsCarryNoSensitiveOriginals() {
    String rrn = "850101-7654321"; // 이 테스트만의 유일값
    String rrnDigits = "8501017654321";
    String mobile = "01098765432";

    String cd = TestSeq.orgCd();
    Long orgId =
        orgService.create(cd, "조직" + cd, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1));

    EmployeeService.HireResult emp =
        employeeService.hire(
            new NewPerson("김민수", rrn, LocalDate.of(1985, 1, 1), Gender.M, mobile, null, "KR"),
            new EmployeeService.HireCommand(
                EmpType.REGULAR, orgId, "STAFF", null, LocalDate.of(2026, 1, 2)));

    AgentService.RegisterResult agent =
        agentService.registerCandidate(
            new NewPerson(
                "이영희", "850101-1112223", LocalDate.of(1985, 1, 1), Gender.F, mobile, null, "KR"),
            new AgentService.RegisterCommand(Channel.FC, orgId, null));

    // 이 테스트가 만든 집계들의 이벤트·변경로그를 훑어 원문 부재를 단언한다.
    assertNoSensitive(outboxOf("ORG", orgId), changeLogOf("ORG", orgId), rrn, rrnDigits, mobile);
    assertNoSensitive(
        outboxOf("EMP", emp.empId()), changeLogOf("EMP", emp.empId()), rrn, rrnDigits, mobile);
    assertNoSensitive(
        outboxOf("AGENT", agent.agentId()),
        changeLogOf("AGENT", agent.agentId()),
        rrn,
        rrnDigits,
        mobile);
    // 최소한 각 집계가 실제로 이벤트를 남겼는지도 확인 — 빈 목록을 통과시키지 않기 위함.
    assertThat(outboxOf("EMP", emp.empId())).isNotEmpty();
    assertThat(outboxOf("AGENT", agent.agentId())).isNotEmpty();
  }

  private List<IfOutbox> outboxOf(String aggType, Long aggId) {
    return outboxRepository.findByAggTypeAndAggIdOrderByIdAsc(aggType, aggId);
  }

  private List<IfChangeLog> changeLogOf(String aggType, Long aggId) {
    return changeLogRepository.findByAggTypeOrderBySeqNoAsc(aggType).stream()
        .filter(l -> l.getAggId().equals(aggId))
        .toList();
  }

  private void assertNoSensitive(
      List<IfOutbox> outbox, List<IfChangeLog> logs, String... forbidden) {
    for (IfOutbox o : outbox) {
      assertThat(o.getPayload()).doesNotContain(forbidden);
    }
    for (IfChangeLog l : logs) {
      assertThat(l.getSnapshotJson()).doesNotContain(forbidden);
    }
  }
}
