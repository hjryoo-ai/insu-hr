package com.portfolio.insuhr.batch.leave;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.batch.support.AbstractBatchIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code annualLeaveGrantJob} — 회계연도 근속 기준 연차 부여 + {@code UQ(EMP_ID, YEAR_NO)} 재실행 멱등 (설계서 8 v2.0).
 */
class AnnualLeaveGrantJobIntegrationTest extends AbstractBatchIntegrationTest {

  @Autowired Job annualLeaveGrantJob;

  @Test
  @DisplayName("근속 3년차는 기본15+가산1=16일 부여, 재실행은 중복 부여 없음")
  void grantsTenureBasedDaysOnce() throws Exception {
    LocalDate targetDate = LocalDate.of(2026, 1, 1);
    long orgId = seedOrg();
    // 2023 입사 → 2026 부여 시 근속 3년 → 가산 (3-1)/2 = 1 → 15+1 = 16.
    long empId = seedEmpHired(seedPerson(), orgId, LocalDate.of(2023, 6, 1), "ACTIVE");

    JobExecution first = runJob(annualLeaveGrantJob, targetDate);
    assertThat(isCompleted(first)).isTrue();
    assertThat(leaveGrantCount(empId, 2026)).isEqualTo(1);
    assertThat(leaveGrantDays(empId, 2026)).isEqualTo(16);

    JobExecution second = runJob(annualLeaveGrantJob, targetDate);
    assertThat(isCompleted(second)).isTrue();
    assertThat(leaveGrantCount(empId, 2026)).as("UQ(EMP_ID, YEAR_NO) — 재부여 없음").isEqualTo(1);
    assertThat(leaveGrantDays(empId, 2026)).isEqualTo(16);
  }
}
