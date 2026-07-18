package com.portfolio.insuhr.batch.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.batch.support.AbstractBatchIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code continuingEduNoticeJob} — 창 안 마일스톤만 부등식 발화 + 조건부 notice.created + 재실행 dedup (설계서 8 v2.0).
 */
class ContinuingEduNoticeJobIntegrationTest extends AbstractBatchIntegrationTest {

  @Autowired Job continuingEduNoticeJob;

  @Test
  @DisplayName("이수기한 25일 전이면 D-60/D-30만 적재(D-7 제외), 재실행은 중복 없이 no-op")
  void enqueuesInWindowMilestonesOnce() throws Exception {
    LocalDate targetDate = LocalDate.of(2026, 7, 18);
    long orgId = seedOrg();
    // 보수교육 이수기한 = 기준일 + 25일 → D-60·D-30 창엔 들고 D-7 창엔 안 든다.
    long agentId = seedActiveEligibleLifeAgent(seedPerson(), orgId, targetDate.plusDays(25));

    JobExecution first = runJob(continuingEduNoticeJob, targetDate);
    assertThat(isCompleted(first)).isTrue();
    assertThat(noticeCount("CONT_EDU_DUE", agentId, "D60")).isEqualTo(1);
    assertThat(noticeCount("CONT_EDU_DUE", agentId, "D30")).isEqualTo(1);
    assertThat(noticeCount("CONT_EDU_DUE", agentId, "D7")).as("25일 전이라 D-7 창 밖").isZero();
    assertThat(noticeCount("CONT_EDU_DUE", agentId)).isEqualTo(2);
    assertThat(outboxCount("AGENT", agentId, "notice.created")).as("생성된 2행에 대해서만 발행").isEqualTo(2);

    JobExecution second = runJob(continuingEduNoticeJob, targetDate);
    assertThat(isCompleted(second)).isTrue();
    assertThat(noticeCount("CONT_EDU_DUE", agentId)).as("dedup").isEqualTo(2);
    assertThat(outboxCount("AGENT", agentId, "notice.created"))
        .as("재실행 enqueue 0 → 무발행")
        .isEqualTo(2);
  }
}
