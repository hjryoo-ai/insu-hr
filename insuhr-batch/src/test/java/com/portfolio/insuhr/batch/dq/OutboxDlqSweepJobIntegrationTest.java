package com.portfolio.insuhr.batch.dq;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.batch.support.AbstractBatchIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code outboxDlqSweepJob} — FAILED 전달·정체 Outbox/Delivery를 관측만 해서 FINDING 적재(재전송 없음) + 재실행 멱등 (설계서
 * 8 v2.0).
 */
class OutboxDlqSweepJobIntegrationTest extends AbstractBatchIntegrationTest {

  @Autowired Job outboxDlqSweepJob;

  @Test
  @DisplayName("한도 소진 FAILED + 오래 머문 READY/PENDING을 각각 FINDING으로, 상태 변경 없이 재실행 멱등")
  void reportsDlqAndStallsWithoutResend() throws Exception {
    LocalDate targetDate = LocalDate.now(); // 정체 판정은 실시계 대비 '오늘 이전'
    long subscriberId = seedSubscriber();

    // ① 한도 소진 FAILED 전달.
    long sentEvent = seedOutbox("AGENT", 100, "agent.a", "SENT", 0);
    long failedDelivery = seedDelivery(sentEvent, subscriberId, "AGENT", 100, "FAILED", 0);

    // ② 이틀째 READY에 머문 Outbox(정체).
    long staleReady = seedOutbox("AGENT", 200, "agent.b", "READY", 2);

    // ③ 이틀째 PENDING에 머문 전달(정체) — 부모 Outbox는 SENT라 OUTBOX_STALL엔 안 걸린다.
    long pendingParent = seedOutbox("AGENT", 300, "agent.c", "SENT", 0);
    long pendingDelivery = seedDelivery(pendingParent, subscriberId, "AGENT", 300, "PENDING", 2);

    JobExecution first = runJob(outboxDlqSweepJob, targetDate);
    assertThat(isCompleted(first)).isTrue();
    assertThat(findingCount("DELIVERY_FAILED", failedDelivery)).isEqualTo(1);
    assertThat(findingCount("OUTBOX_STALL", staleReady)).isEqualTo(1);
    assertThat(findingCount("DELIVERY_STALL_PENDING", pendingDelivery)).isEqualTo(1);

    // 관측만 — 원 레코드 상태는 그대로.
    assertThat(
            jdbcClient
                .sql("SELECT STATUS_CD FROM TB_IF_DELIVERY WHERE DELIVERY_ID = :id")
                .param("id", failedDelivery)
                .query(String.class)
                .single())
        .as("재전송 안 함 — FAILED 유지")
        .isEqualTo("FAILED");

    JobExecution second = runJob(outboxDlqSweepJob, targetDate);
    assertThat(isCompleted(second)).isTrue();
    assertThat(findingCount("DELIVERY_FAILED", failedDelivery)).as("dedup").isEqualTo(1);
    assertThat(findingCount("OUTBOX_STALL", staleReady)).isEqualTo(1);
    assertThat(findingCount("DELIVERY_STALL_PENDING", pendingDelivery)).isEqualTo(1);
  }
}
