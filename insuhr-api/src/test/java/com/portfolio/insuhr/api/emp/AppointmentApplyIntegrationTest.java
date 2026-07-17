package com.portfolio.insuhr.api.emp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.MutableClock;
import com.portfolio.insuhr.api.support.TestClockConfig;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.emp.AppointType;
import com.portfolio.insuhr.domain.emp.AppointmentApplyService;
import com.portfolio.insuhr.domain.emp.Emp;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.emp.EmpStatus;
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
import org.springframework.context.annotation.Import;

/**
 * 발령 반영 규칙 (설계서 5.5 v1.4, 시나리오 6a).
 *
 * <p>이 시나리오의 위험은 배치 배관이 아니라 반영 규칙 자체다 — 날짜 경계, 같은 날 다중 발령의 순서, 취소와의 상호작용, 재계산 멱등성. 배치({@code
 * futureAppointApplyJob}, Phase 7)는 이 규칙을 감싸기만 하므로(6b), 규칙을 여기서 배치 없이 검증한다.
 *
 * <p>{@link MutableClock}으로 "오늘"을 옮겨 가며 확인한다.
 */
@Import(TestClockConfig.class)
class AppointmentApplyIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired EmployeeService employeeService;
  @Autowired AppointmentApplyService applyService;
  @Autowired EmpRepository empRepository;
  @Autowired OrgRepository orgRepository;
  @Autowired MutableClock clock;

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
        "직원" + n, TestSeq.rrn(), LocalDate.of(1990, 1, 1), Gender.M, "01012341234", null, "KR");
  }

  /** 특정 입사일의 직원을 만든다. */
  private Long hireOn(Long orgId, LocalDate hireDt) {
    return employeeService
        .hire(
            newPerson(),
            new EmployeeService.HireCommand(EmpType.REGULAR, orgId, "STAFF", null, hireDt))
        .empId();
  }

  private Emp reload(Long empId) {
    return empRepository.findById(empId).orElseThrow();
  }

  @Test
  @DisplayName("미래 발령을 확정해도 발령일 전까지는 스냅샷에 반영되지 않고, 발령일이 되면 재계산이 반영한다")
  void futureAppointmentAppliesOnlyOnItsDate() {
    clock.setDate(LocalDate.of(2026, 8, 1));
    Long fromOrg = newOrg();
    Long toOrg = newOrg();
    Long empId = hireOn(fromOrg, LocalDate.of(2026, 8, 1));

    // 9/1자 전보를 8/1에 확정한다 (미래 발령).
    Long appointId =
        employeeService.draftAppointment(
            empId,
            new EmployeeService.AppointmentCommand(
                AppointType.TRANSFER, LocalDate.of(2026, 9, 1), toOrg, "SENIOR", null, "전보"));
    employeeService.confirmAppointment(appointId);

    // 아직 8/1 — 스냅샷은 입사 소속 그대로여야 한다.
    assertThat(reload(empId).getOrgId()).isEqualTo(fromOrg);
    assertThat(reload(empId).getJobGradeCd()).isEqualTo("STAFF");

    // 발령일 하루 전 — 여전히 미반영.
    clock.setDate(LocalDate.of(2026, 8, 31));
    assertThat(applyService.recalculate(empId)).isFalse();
    assertThat(reload(empId).getOrgId()).isEqualTo(fromOrg);

    // 발령일 당일 — 재계산이 반영한다.
    clock.setDate(LocalDate.of(2026, 9, 1));
    assertThat(applyService.recalculate(empId)).isTrue();
    assertThat(reload(empId).getOrgId()).isEqualTo(toOrg);
    assertThat(reload(empId).getJobGradeCd()).isEqualTo("SENIOR");
  }

  @Test
  @DisplayName("같은 날 발령이 여러 건이면 APPOINT_ID가 큰(나중 기안) 발령이 스냅샷을 결정한다")
  void sameDayAppointmentsResolveByAppointId() {
    clock.setDate(LocalDate.of(2026, 8, 1));
    Long org = newOrg();
    Long orgA = newOrg();
    Long orgB = newOrg();
    Long empId = hireOn(org, LocalDate.of(2026, 8, 1));

    // 같은 발령일(9/1)에 두 건을 기안·확정한다. 나중에 기안한 B가 이겨야 한다.
    Long first =
        employeeService.draftAppointment(
            empId,
            new EmployeeService.AppointmentCommand(
                AppointType.TRANSFER, LocalDate.of(2026, 9, 1), orgA, "SENIOR", null, "먼저"));
    Long second =
        employeeService.draftAppointment(
            empId,
            new EmployeeService.AppointmentCommand(
                AppointType.TRANSFER, LocalDate.of(2026, 9, 1), orgB, "MANAGER", null, "나중"));
    assertThat(second).isGreaterThan(first);

    employeeService.confirmAppointment(first);
    employeeService.confirmAppointment(second);

    clock.setDate(LocalDate.of(2026, 9, 1));
    applyService.recalculate(empId);

    assertThat(reload(empId).getOrgId()).isEqualTo(orgB);
    assertThat(reload(empId).getJobGradeCd()).isEqualTo("MANAGER");
  }

  @Test
  @DisplayName("재계산은 몇 번을 돌려도 결과가 같다 — 멱등성이 함수 정의에서 나온다")
  void recalculationIsIdempotent() {
    clock.setDate(LocalDate.of(2026, 9, 1));
    Long org = newOrg();
    Long toOrg = newOrg();
    Long empId = hireOn(org, LocalDate.of(2026, 8, 1));

    Long appointId =
        employeeService.draftAppointment(
            empId,
            new EmployeeService.AppointmentCommand(
                AppointType.PROMOTION, LocalDate.of(2026, 9, 1), toOrg, "SENIOR", null, "승진"));
    employeeService.confirmAppointment(appointId);

    // 첫 재계산은 이미 확정 시점에 반영됐으므로 두 번째 이후는 항상 '변화 없음'이어야 한다.
    assertThat(applyService.recalculate(empId)).isFalse();
    assertThat(applyService.recalculate(empId)).isFalse();
    assertThat(reload(empId).getOrgId()).isEqualTo(toOrg);
    assertThat(reload(empId).getJobGradeCd()).isEqualTo("SENIOR");
  }

  @Test
  @DisplayName("이미 반영된 발령은 취소할 수 없다 — 정정 발령으로만 되돌린다")
  void appliedAppointmentCannotBeCanceled() {
    clock.setDate(LocalDate.of(2026, 9, 1));
    Long org = newOrg();
    Long toOrg = newOrg();
    Long empId = hireOn(org, LocalDate.of(2026, 8, 1));

    Long appointId =
        employeeService.draftAppointment(
            empId,
            new EmployeeService.AppointmentCommand(
                AppointType.TRANSFER, LocalDate.of(2026, 9, 1), toOrg, "SENIOR", null, "전보"));
    employeeService.confirmAppointment(appointId); // 오늘=9/1이라 즉시 반영됨

    assertThatThrownBy(() -> employeeService.cancelAppointment(appointId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("이미 반영된 발령");
  }

  @Test
  @DisplayName("확정됐지만 발령일이 미래인 예약분은 취소할 수 있다")
  void futureConfirmedAppointmentCanBeCanceled() {
    clock.setDate(LocalDate.of(2026, 8, 1));
    Long org = newOrg();
    Long toOrg = newOrg();
    Long empId = hireOn(org, LocalDate.of(2026, 8, 1));

    Long appointId =
        employeeService.draftAppointment(
            empId,
            new EmployeeService.AppointmentCommand(
                AppointType.TRANSFER, LocalDate.of(2026, 9, 1), toOrg, "SENIOR", null, "전보"));
    employeeService.confirmAppointment(appointId);

    // 8/1에는 9/1 예약분이 아직 미반영이라 취소 가능.
    employeeService.cancelAppointment(appointId);

    // 발령일이 지나도 취소된 발령은 스냅샷에 반영되지 않는다.
    clock.setDate(LocalDate.of(2026, 9, 1));
    assertThat(applyService.recalculate(empId)).isFalse();
    assertThat(reload(empId).getOrgId()).isEqualTo(org);
  }

  @Test
  @DisplayName("퇴직 발령이 반영되면 재직상태가 RESIGNED로, 퇴직일이 채워진다")
  void resignAppointmentSetsResignedStatus() {
    clock.setDate(LocalDate.of(2026, 12, 31));
    Long org = newOrg();
    Long empId = hireOn(org, LocalDate.of(2026, 8, 1));

    Long appointId =
        employeeService.draftAppointment(
            empId,
            new EmployeeService.AppointmentCommand(
                AppointType.RESIGN, LocalDate.of(2026, 12, 31), org, "STAFF", null, "자진 퇴사"));
    employeeService.confirmAppointment(appointId);

    Emp emp = reload(empId);
    assertThat(emp.getStatus()).isEqualTo(EmpStatus.RESIGNED);
    assertThat(emp.getResignDt()).isEqualTo(LocalDate.of(2026, 12, 31));
  }
}
