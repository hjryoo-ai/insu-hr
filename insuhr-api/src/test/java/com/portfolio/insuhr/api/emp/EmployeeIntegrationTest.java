package com.portfolio.insuhr.api.emp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.emp.AppointType;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.emp.EmpType;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import com.portfolio.insuhr.domain.person.PersonRepository;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 입사 처리 (설계서 7.2 {@code POST /employees}, 5.2). */
class EmployeeIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired EmployeeService employeeService;
  @Autowired EmpRepository empRepository;
  @Autowired PersonRepository personRepository;
  @Autowired OrgRepository orgRepository;

  private Long newOrg() {
    int n = SEQ.getAndIncrement();
    return orgRepository
        .save(
            Org.create(
                TestSeq.orgCd(), "조직" + n, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1)))
        .getId();
  }

  private NewPerson personWithRrn(String rrn) {
    int n = SEQ.getAndIncrement();
    return new NewPerson(
        "직원" + n, rrn, LocalDate.of(1990, 1, 1), Gender.M, "01012341234", null, "KR");
  }

  private String uniqueRrn() {
    return TestSeq.rrn();
  }

  private EmployeeService.HireCommand hireCmd(Long orgId, LocalDate hireDt) {
    return new EmployeeService.HireCommand(EmpType.REGULAR, orgId, "STAFF", null, hireDt);
  }

  @Test
  @DisplayName("입사하면 인물·임직원·입사발령이 함께 생기고 사번은 시퀀스로 채번된다")
  void hireCreatesPersonEmpAndAppointment() {
    Long orgId = newOrg();
    EmployeeService.HireResult result =
        employeeService.hire(personWithRrn(uniqueRrn()), hireCmd(orgId, LocalDate.of(2026, 3, 2)));

    assertThat(result.empId()).isNotNull();
    assertThat(result.personId()).isNotNull();
    // 사번은 의미 없는 시퀀스 번호 E + 8자리 (설계서 6.4)
    assertThat(result.empNo()).matches("E\\d{8}");

    var emp = empRepository.findById(result.empId()).orElseThrow();
    assertThat(emp.getOrgId()).isEqualTo(orgId);
    assertThat(emp.getHireDt()).isEqualTo(LocalDate.of(2026, 3, 2));
    assertThat(emp.getStatus().name()).isEqualTo("ACTIVE");
  }

  @Test
  @DisplayName("사번은 입사할 때마다 서로 다르게 발급된다")
  void empNoIsUniquePerHire() {
    Long orgId = newOrg();
    String a =
        employeeService
            .hire(personWithRrn(uniqueRrn()), hireCmd(orgId, LocalDate.of(2026, 3, 2)))
            .empNo();
    String b =
        employeeService
            .hire(personWithRrn(uniqueRrn()), hireCmd(orgId, LocalDate.of(2026, 3, 2)))
            .empNo();
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  @DisplayName("같은 주민번호로 두 번 입사시키면 인물은 하나로 유지되고 두 번째는 거부된다")
  void sameRrnCannotBecomeEmployeeTwice() {
    Long orgId = newOrg();
    String rrn = uniqueRrn();
    employeeService.hire(personWithRrn(rrn), hireCmd(orgId, LocalDate.of(2026, 3, 2)));

    assertThatThrownBy(
            () ->
                employeeService.hire(personWithRrn(rrn), hireCmd(orgId, LocalDate.of(2026, 3, 2))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("이미 임직원");

    // 인물은 재사용됐어야 한다 (설계서 5.2) — 중복 인물이 생기지 않았다.
    assertThat(personRepository.findByRrnHash(hashOf(rrn))).isPresent();
  }

  @Test
  @DisplayName("폐지된 조직으로는 입사시킬 수 없다")
  void cannotHireIntoClosedOrg() {
    Org org =
        Org.create(TestSeq.orgCd(), "폐지조직", OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1));
    org.close(LocalDate.of(2020, 1, 1));
    Long orgId = orgRepository.save(org).getId();

    assertThatThrownBy(
            () ->
                employeeService.hire(
                    personWithRrn(uniqueRrn()), hireCmd(orgId, LocalDate.of(2026, 3, 2))))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("조직");
  }

  @Test
  @DisplayName("발령일이 입사일보다 앞서면 기안이 거부된다")
  void appointmentBeforeHireDateRejected() {
    Long orgId = newOrg();
    Long empId =
        employeeService
            .hire(personWithRrn(uniqueRrn()), hireCmd(orgId, LocalDate.of(2026, 3, 2)))
            .empId();

    assertThatThrownBy(
            () ->
                employeeService.draftAppointment(
                    empId,
                    new EmployeeService.AppointmentCommand(
                        AppointType.PROMOTION,
                        LocalDate.of(2026, 1, 1),
                        orgId,
                        "SENIOR",
                        null,
                        "승진")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("입사일");
  }

  // 해시는 도메인 유틸을 그대로 쓴다 — 테스트가 해시 방식을 재구현하면 방식이 갈릴 때 조용히 어긋난다.
  @Autowired com.portfolio.insuhr.common.crypto.PepperedHasher rrnHasher;

  private String hashOf(String rrn) {
    return rrnHasher.hash(rrn);
  }
}
