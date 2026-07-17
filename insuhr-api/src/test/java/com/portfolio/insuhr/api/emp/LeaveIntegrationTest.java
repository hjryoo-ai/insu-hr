package com.portfolio.insuhr.api.emp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.emp.EmpType;
import com.portfolio.insuhr.domain.leave.LeaveStatus;
import com.portfolio.insuhr.domain.leave.LeaveType;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 휴가·연차 (설계서 7.2, 6.5). 연차 차감이 신청이 아니라 승인 시점임을 확인한다. */
class LeaveIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired EmployeeService employeeService;
  @Autowired LeaveService leaveService;
  @Autowired OrgRepository orgRepository;

  private Long anEmp() {
    int n = SEQ.getAndIncrement();
    Long orgId =
        orgRepository
            .save(
                Org.create(
                    TestSeq.orgCd(), "조직" + n, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1)))
            .getId();
    return employeeService
        .hire(
            new NewPerson(
                "직원" + n,
                TestSeq.rrn(),
                LocalDate.of(1990, 1, 1),
                Gender.M,
                "01012341234",
                null,
                "KR"),
            new EmployeeService.HireCommand(
                EmpType.REGULAR, orgId, "STAFF", null, LocalDate.of(2026, 1, 2)))
        .empId();
  }

  @Test
  @DisplayName("연차 신청만으로는 잔여가 줄지 않고, 승인 시점에 차감된다")
  void annualDeductedOnApprovalNotRequest() {
    Long empId = anEmp();
    leaveService.grant(empId, 2026, new BigDecimal("15.0"), LocalDate.of(2026, 12, 31));

    Long reqId =
        leaveService.requestLeave(
            empId,
            LeaveType.ANNUAL,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 3),
            new BigDecimal("3.0"),
            "가족여행");

    // 신청만 한 상태 — 잔여는 그대로 15.
    assertThat(leaveService.grants(empId).get(0).remainingDays()).isEqualByComparingTo("15.0");

    leaveService.approve(reqId, empId);
    // 승인 후 3일 차감 → 12.
    assertThat(leaveService.grants(empId).get(0).remainingDays()).isEqualByComparingTo("12.0");
  }

  @Test
  @DisplayName("승인된 연차를 취소하면 차감이 복원된다")
  void cancelingApprovedAnnualRestoresBalance() {
    Long empId = anEmp();
    leaveService.grant(empId, 2026, new BigDecimal("15.0"), LocalDate.of(2026, 12, 31));
    Long reqId =
        leaveService.requestLeave(
            empId,
            LeaveType.ANNUAL,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 2),
            new BigDecimal("2.0"),
            "휴식");
    leaveService.approve(reqId, empId);
    assertThat(leaveService.grants(empId).get(0).remainingDays()).isEqualByComparingTo("13.0");

    leaveService.cancel(reqId);
    assertThat(leaveService.grants(empId).get(0).remainingDays()).isEqualByComparingTo("15.0");
  }

  @Test
  @DisplayName("병가는 연차에서 차감되지 않는다")
  void sickLeaveDoesNotDeductAnnual() {
    Long empId = anEmp();
    leaveService.grant(empId, 2026, new BigDecimal("15.0"), LocalDate.of(2026, 12, 31));
    Long reqId =
        leaveService.requestLeave(
            empId,
            LeaveType.SICK,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 5),
            new BigDecimal("5.0"),
            "감기");
    leaveService.approve(reqId, empId);

    assertThat(leaveService.grants(empId).get(0).remainingDays()).isEqualByComparingTo("15.0");
  }

  @Test
  @DisplayName("잔여를 초과하는 연차 승인은 거부되고, 승인 자체가 롤백된다")
  void approvalExceedingBalanceIsRejected() {
    Long empId = anEmp();
    leaveService.grant(empId, 2026, new BigDecimal("2.0"), LocalDate.of(2026, 12, 31));
    Long reqId =
        leaveService.requestLeave(
            empId,
            LeaveType.ANNUAL,
            LocalDate.of(2026, 5, 1),
            LocalDate.of(2026, 5, 5),
            new BigDecimal("5.0"),
            "장기휴가");

    assertThatThrownBy(() -> leaveService.approve(reqId, empId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("잔여");

    // 승인이 롤백됐으니 신청은 여전히 REQUESTED, 잔여도 그대로.
    assertThat(leaveService.requests(empId).get(0).getStatus()).isEqualTo(LeaveStatus.REQUESTED);
    assertThat(leaveService.grants(empId).get(0).remainingDays()).isEqualByComparingTo("2.0");
  }

  @Test
  @DisplayName("같은 연도 연차를 두 번 부여하면 거부된다")
  void duplicateGrantRejected() {
    Long empId = anEmp();
    leaveService.grant(empId, 2026, new BigDecimal("15.0"), LocalDate.of(2026, 12, 31));
    assertThatThrownBy(
            () ->
                leaveService.grant(empId, 2026, new BigDecimal("15.0"), LocalDate.of(2026, 12, 31)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("이미");
  }
}
