package com.portfolio.insuhr.batch.dq;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.batch.support.AbstractBatchIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

/** {@code dataQualityJob} — 조직 구조 결손(폐지 조직 재직자·협회 전무 설계사)을 FINDING 적재 + 재실행 멱등 (설계서 8 v2.0). */
class DataQualityJobIntegrationTest extends AbstractBatchIntegrationTest {

  @Autowired Job dataQualityJob;

  @Test
  @DisplayName("폐지 조직 재직자 + 협회등록 전무 ACTIVE 설계사 각 1건, 재실행 dedup")
  void reportsStructuralGapsIdempotently() throws Exception {
    LocalDate targetDate = LocalDate.of(2026, 7, 18);

    long abolishedOrg = seedAbolishedOrg();
    long empId = seedEmpHired(seedPerson(), abolishedOrg, LocalDate.of(2024, 1, 1), "ACTIVE");

    long normalOrg = seedOrg();
    long agentId = seedBareActiveAgent(seedPerson(), normalOrg); // 협회등록 없음

    JobExecution first = runJob(dataQualityJob, targetDate);
    assertThat(isCompleted(first)).isTrue();
    assertThat(findingCount("EMP_ORG_ABOLISHED", empId)).isEqualTo(1);
    assertThat(findingCount("AGENT_ACTIVE_NO_ASSOC", agentId)).isEqualTo(1);

    JobExecution second = runJob(dataQualityJob, targetDate);
    assertThat(isCompleted(second)).isTrue();
    assertThat(findingCount("EMP_ORG_ABOLISHED")).as("dedup").isEqualTo(1);
    assertThat(findingCount("AGENT_ACTIVE_NO_ASSOC")).isEqualTo(1);
  }
}
