package com.portfolio.insuhr.api.emp;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.FixedDawnClockConfig;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.domain.emp.AppointType;
import com.portfolio.insuhr.domain.emp.AppointmentApplyService;
import com.portfolio.insuhr.domain.emp.Emp;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.emp.EmpType;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 시각/날짜 파생 규칙의 통합 앵커 (설계서 6.2 v1.5, Phase 3 실증).
 *
 * <p>시계를 {@code 2026-08-01T00:05+09:00}에 고정하고, 그 시점(UTC로는 아직 7월 31일)에 <b>8월 1일자 발령이 반영</b>됨을 실제
 * Oracle 위에서 확인한다. UTC 존이었다면 업무 날짜가 7월 31일로 계산돼 반영이 하루 늦었을 것이다 — 새벽 {@code
 * futureAppointApplyJob}(Phase 7)이 바로 이 경계를 밟는다.
 */
@Import(FixedDawnClockConfig.class)
class ClockBoundaryIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired EmployeeService employeeService;
  @Autowired AppointmentApplyService applyService;
  @Autowired EmpRepository empRepository;
  @Autowired OrgRepository orgRepository;
  @Autowired Clock clock;

  private Long newOrg() {
    int n = SEQ.getAndIncrement();
    return orgRepository
        .save(
            Org.create(
                TestSeq.orgCd(), "조직" + n, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1)))
        .getId();
  }

  @Test
  @DisplayName("새벽 00:05 KST: 업무 날짜는 8월 1일이고, 8월 1일자 발령이 반영된다 (UTC로는 아직 7월 31일)")
  void aug1AppointmentAppliesAtDawnKstEvenThoughUtcIsStillJul31() {
    // 업무 날짜는 8월 1일.
    assertThat(applyService.today()).isEqualTo(LocalDate.of(2026, 8, 1));
    // 같은 절대시각을 UTC로 보면 아직 7월 31일 — Instant 저장 규약은 흔들리지 않는다.
    assertThat(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))
        .isEqualTo(LocalDate.of(2026, 7, 31));

    Long fromOrg = newOrg();
    Long toOrg = newOrg();
    int n = SEQ.getAndIncrement();
    Long empId =
        employeeService
            .hire(
                new NewPerson(
                    "직원" + n,
                    TestSeq.rrn(),
                    LocalDate.of(1990, 1, 1),
                    Gender.M,
                    "01011112222",
                    null,
                    "KR"),
                new EmployeeService.HireCommand(
                    EmpType.REGULAR, fromOrg, "STAFF", null, LocalDate.of(2026, 8, 1)))
            .empId();

    // 8월 1일자 전보를 확정하면, 업무 날짜가 8월 1일이므로 즉시 반영된다.
    Long appointId =
        employeeService.draftAppointment(
            empId,
            new EmployeeService.AppointmentCommand(
                AppointType.TRANSFER, LocalDate.of(2026, 8, 1), toOrg, "SENIOR", null, "전보"));
    employeeService.confirmAppointment(appointId);

    Emp emp = empRepository.findById(empId).orElseThrow();
    assertThat(emp.getOrgId()).as("8월 1일자 발령이 반영돼 소속이 바뀌어야 한다").isEqualTo(toOrg);
  }
}
