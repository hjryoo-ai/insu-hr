package com.portfolio.insuhr.batch.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.batch.support.AbstractBatchIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code privacyPurgeJob} — 두 대상군 익명화 + 대장 + person.purged + 재실행 멱등, 그리고 <b>모든 역할 종료</b> 가드 (설계서 8
 * Phase 8).
 */
class PrivacyPurgeJobIntegrationTest extends AbstractBatchIntegrationTest {

  @Autowired Job privacyPurgeJob;

  @Test
  @DisplayName("역할 종료+보존기간 경과 인물은 익명화(RRN·주소 파기)+대장(ROLE_ENDED)+person.purged, 재실행 멱등")
  void purgesRoleEndedPersonIdempotently() throws Exception {
    LocalDate targetDate = LocalDate.now();
    long orgId = seedOrg();
    long personId = seedPerson();
    seedAddress(personId);
    seedResignedEmp(personId, orgId, targetDate.minusYears(10)); // 보존기간 훨씬 경과

    JobExecution first = runJob(privacyPurgeJob, targetDate);
    assertThat(isCompleted(first)).isTrue();
    assertThat(personAnonymized(personId)).as("RRN_ENC·RRN_HASH NULL").isTrue();
    assertThat(personAddrCount(personId)).as("주소(암호화) 파기").isZero();
    assertThat(purgeLedgerType(personId)).isEqualTo("ROLE_ENDED");
    assertThat(outboxCount("PERSON", personId, "person.purged")).isEqualTo(1);

    JobExecution second = runJob(privacyPurgeJob, targetDate);
    assertThat(isCompleted(second)).isTrue();
    assertThat(purgeLedgerCount(personId)).as("RRN_HASH NULL → 대상 술어에서 배제, 재파기 없음").isEqualTo(1);
    assertThat(outboxCount("PERSON", personId, "person.purged")).isEqualTo(1);
  }

  @Test
  @DisplayName("이중 역할은 둘 다 종료돼야 파기 — 하나라도 재직/활성이면 살린다")
  void purgesOnlyWhenAllRolesEnded() throws Exception {
    LocalDate targetDate = LocalDate.now();
    long orgId = seedOrg();

    // 둘 다 종료: 직원 퇴직 + 설계사 해촉 → 파기.
    long bothEnded = seedPerson();
    seedResignedEmp(bothEnded, orgId, targetDate.minusYears(10));
    seedTerminatedAgent(bothEnded, orgId, targetDate.minusYears(10));

    // 직원은 퇴직했지만 설계사는 ACTIVE → 살린다(한 역할만 보고 지우면 재직자 주민번호가 날아간다).
    long stillActive = seedPerson();
    seedResignedEmp(stillActive, orgId, targetDate.minusYears(10));
    seedBareActiveAgent(stillActive, orgId);

    JobExecution exec = runJob(privacyPurgeJob, targetDate);
    assertThat(isCompleted(exec)).isTrue();

    assertThat(personAnonymized(bothEnded)).as("모든 역할 종료 → 파기").isTrue();
    assertThat(purgeLedgerType(bothEnded)).isEqualTo("ROLE_ENDED");

    assertThat(personAnonymized(stillActive)).as("설계사 ACTIVE → 파기 대상 아님").isFalse();
    assertThat(purgeLedgerCount(stillActive)).isZero();
  }

  @Test
  @DisplayName("무역할 인물은 유예 경과분만 ORPHAN 파기, 최근 생성분·보존기간 미경과는 살린다")
  void purgesOrphansPastGraceOnly() throws Exception {
    LocalDate targetDate = LocalDate.now();
    long orgId = seedOrg();

    long oldOrphan = seedPersonCreatedDaysAgo(40); // 유예(30일) 경과 → 파기
    long freshOrphan = seedPersonCreatedDaysAgo(10); // 유예 이내 → 살림

    // 역할은 종료됐지만 보존기간(5년) 미경과 → 살림.
    long recentlyResigned = seedPerson();
    seedResignedEmp(recentlyResigned, orgId, targetDate.minusYears(1));

    JobExecution exec = runJob(privacyPurgeJob, targetDate);
    assertThat(isCompleted(exec)).isTrue();

    assertThat(personAnonymized(oldOrphan)).as("유예 경과 무역할 → 파기").isTrue();
    assertThat(purgeLedgerType(oldOrphan)).isEqualTo("ORPHAN");

    assertThat(personAnonymized(freshOrphan)).as("유예 이내 → 살림").isFalse();
    assertThat(personAnonymized(recentlyResigned)).as("보존기간 미경과 → 살림").isFalse();
  }
}
