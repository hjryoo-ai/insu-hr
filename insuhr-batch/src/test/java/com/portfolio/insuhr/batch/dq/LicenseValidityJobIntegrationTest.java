package com.portfolio.insuhr.batch.dq;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.batch.support.AbstractBatchIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

/** {@code licenseValidityJob} — 살아있는 설계사의 자격 상태 모순을 FINDING 적재 + 재실행 멱등 (설계서 8 v2.0). */
class LicenseValidityJobIntegrationTest extends AbstractBatchIntegrationTest {

  @Autowired Job licenseValidityJob;

  @Test
  @DisplayName("ACTIVE인데 REVOKED 자격·DEREGISTERED 협회면 각 룰 1건, 재실행은 dedup")
  void reportsCredentialContradictionsIdempotently() throws Exception {
    LocalDate targetDate = LocalDate.of(2026, 7, 18);
    long orgId = seedOrg();
    long agentId = seedBareActiveAgent(seedPerson(), orgId);
    seedLicense(agentId, "LIFE", "REVOKED");
    seedAssoc(agentId, "LIFE_ASSOC", "DEREGISTERED");

    JobExecution first = runJob(licenseValidityJob, targetDate);
    assertThat(isCompleted(first)).isTrue();
    assertThat(findingCount("LICENSE_REVOKED_ACTIVE", agentId)).isEqualTo(1);
    assertThat(findingCount("ASSOC_DEREG_ACTIVE", agentId)).isEqualTo(1);

    JobExecution second = runJob(licenseValidityJob, targetDate);
    assertThat(isCompleted(second)).isTrue();
    assertThat(findingCount("LICENSE_REVOKED_ACTIVE")).as("UQ(룰,대상,일자) dedup").isEqualTo(1);
    assertThat(findingCount("ASSOC_DEREG_ACTIVE")).isEqualTo(1);
  }
}
