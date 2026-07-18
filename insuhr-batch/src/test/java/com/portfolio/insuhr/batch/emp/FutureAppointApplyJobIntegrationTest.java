package com.portfolio.insuhr.batch.emp;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.batch.support.AbstractBatchIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 시나리오 6b — {@code futureAppointApplyJob}이 {@code AppointmentApplyService}를 올바로 감쌌는가 (설계서 12,
 * 13.2).
 *
 * <p>대상 추출(발령일 도래) · 청크 처리(재계산) · 재실행 멱등 · Outbox 발행을 배치 배관 층에서 검증한다. 반영 규칙 자체(6a)는 도메인 테스트가 이미
 * 지킨다.
 */
class FutureAppointApplyJobIntegrationTest extends AbstractBatchIntegrationTest {

  @Autowired Job futureAppointApplyJob;

  @Test
  @DisplayName("발령일 도래분을 반영하고 이벤트를 발행하며, 재실행해도 결과·이벤트가 그대로다(멱등)")
  void appliesDueTransferAndIsIdempotent() throws Exception {
    long fromOrg = seedOrg();
    long toOrg = seedOrg();
    long empId = seedEmp(seedPerson(), fromOrg);
    LocalDate appointDt = LocalDate.of(2026, 7, 18);
    seedConfirmedTransfer(empId, appointDt, toOrg);

    assertThat(orgOf(empId)).as("반영 전 원 소속").isEqualTo(fromOrg);

    JobExecution first = runJob(futureAppointApplyJob, appointDt);
    assertThat(isCompleted(first)).isTrue();
    assertThat(orgOf(empId)).as("발령일 도래 → 스냅샷 반영").isEqualTo(toOrg);
    assertThat(outboxCount("EMP", empId, "emp.appointment.applied")).as("반영 이벤트 1건").isEqualTo(1);

    JobExecution second = runJob(futureAppointApplyJob, appointDt);
    assertThat(isCompleted(second)).isTrue();
    assertThat(orgOf(empId)).as("재실행해도 결과 동일").isEqualTo(toOrg);
    assertThat(outboxCount("EMP", empId, "emp.appointment.applied"))
        .as("변화 없어 추가 발행 없음(멱등)")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("발령일이 아직 도래하지 않은 발령은 대상에서 빠져 미반영이다(날짜 경계)")
  void futureDatedAppointmentNotYetApplied() throws Exception {
    long fromOrg = seedOrg();
    long toOrg = seedOrg();
    long empId = seedEmp(seedPerson(), fromOrg);
    seedConfirmedTransfer(empId, LocalDate.of(2026, 8, 1), toOrg);

    JobExecution exec = runJob(futureAppointApplyJob, LocalDate.of(2026, 7, 18));
    assertThat(isCompleted(exec)).isTrue();
    assertThat(orgOf(empId)).as("발령일 미도래 → 미반영").isEqualTo(fromOrg);
    assertThat(outboxCount("EMP", empId, "emp.appointment.applied")).isZero();
  }
}
