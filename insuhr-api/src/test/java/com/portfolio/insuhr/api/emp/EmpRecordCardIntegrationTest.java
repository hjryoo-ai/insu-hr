package com.portfolio.insuhr.api.emp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.emp.EmpType;
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

/** 인사기록카드 6종 CRUD (설계서 7.2, 6.5). */
class EmpRecordCardIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired EmployeeService employeeService;
  @Autowired EmpRecordCardService service;
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
  @DisplayName("학력을 추가·수정·삭제하고 목록으로 확인한다")
  void schoolCrud() {
    Long empId = anEmp();
    Long id =
        service.addSchool(empId, "한국대", "경영", "BACHELOR", LocalDate.of(2013, 2, 20), "GRADUATED");
    assertThat(service.schools(empId)).hasSize(1);

    service.updateSchool(id, "한국대학교", "경제", "BACHELOR", LocalDate.of(2013, 2, 20), "GRADUATED");
    assertThat(service.schools(empId).get(0).getMajorNm()).isEqualTo("경제");

    service.deleteSchool(id);
    assertThat(service.schools(empId)).isEmpty();
  }

  @Test
  @DisplayName("경력·가족·자격증·교육·상벌을 각각 추가하면 목록에 나타난다")
  void otherRecordCardsAddAndList() {
    Long empId = anEmp();

    service.addCareer(
        empId, "전직장", "영업부", "대리", LocalDate.of(2015, 1, 1), LocalDate.of(2020, 12, 31), "법인영업");
    service.addFamily(empId, "SPOUSE", "김배우자", LocalDate.of(1991, 5, 5), true);
    service.addCert(empId, "정보처리기사", "큐넷", "12345", LocalDate.of(2014, 8, 1), null);
    service.addEdu(
        empId,
        "신입교육",
        "REG",
        LocalDate.of(2026, 1, 5),
        LocalDate.of(2026, 1, 9),
        new BigDecimal("40.0"),
        "COMPLETED");
    service.addRewardPunish(empId, "REWARD", "COMMENDATION", LocalDate.of(2026, 3, 1), "우수사원");

    assertThat(service.careers(empId)).hasSize(1);
    assertThat(service.families(empId)).hasSize(1);
    assertThat(service.certs(empId)).hasSize(1);
    assertThat(service.educations(empId)).hasSize(1);
    assertThat(service.rewardPunishments(empId)).hasSize(1);
  }

  @Test
  @DisplayName("경력의 퇴사일이 입사일보다 앞서면 거부된다")
  void careerRejectsInvertedPeriod() {
    Long empId = anEmp();
    assertThatThrownBy(
            () ->
                service.addCareer(
                    empId,
                    "전직장",
                    null,
                    null,
                    LocalDate.of(2020, 1, 1),
                    LocalDate.of(2015, 1, 1),
                    null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("존재하지 않는 임직원에 기록을 달면 거부된다")
  void rejectsUnknownEmp() {
    assertThatThrownBy(
            () -> service.addSchool(999_999L, "한국대", null, "BACHELOR", null, "GRADUATED"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("임직원");
  }
}
