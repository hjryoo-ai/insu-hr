package com.portfolio.insuhr.api.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.api.org.OrgService;
import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.domain.integration.IfChangeLog;
import com.portfolio.insuhr.domain.integration.IfChangeLogRepository;
import com.portfolio.insuhr.domain.integration.IfOutbox;
import com.portfolio.insuhr.domain.integration.IfOutboxRepository;
import com.portfolio.insuhr.domain.integration.OutboxStatus;
import com.portfolio.insuhr.domain.org.OrgType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * IntegrationRecorder 실구현 검증 (설계서 9.2, Phase 6).
 *
 * <p>Phase 2~5의 "recorder가 1회 호출됨" 단언을 <b>실제 행 존재</b>로 승격한다(13.2 v1.6). {@code
 * RecordingIntegrationRecorder}를 import하지 않으므로 실구현 {@code OutboxIntegrationRecorder}가 쓰여
 * TB_IF_OUTBOX·TB_IF_CHANGE_LOG에 기록된다.
 */
class OutboxRecorderIntegrationTest extends AbstractIntegrationTest {

  @Autowired OrgService orgService;
  @Autowired IfOutboxRepository outboxRepository;
  @Autowired IfChangeLogRepository changeLogRepository;

  @Test
  @DisplayName("조직 신설이 Outbox(READY, 9.3 페이로드)와 ChangeLog(전체 스냅샷)에 한 트랜잭션으로 남는다")
  void orgCreateWritesOutboxAndChangeLog() {
    String cd = TestSeq.orgCd();
    Long orgId =
        orgService.create(cd, "조직" + cd, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1));

    List<IfOutbox> outbox = outboxRepository.findByAggTypeAndAggIdOrderByIdAsc("ORG", orgId);
    assertThat(outbox).hasSize(1);
    IfOutbox event = outbox.get(0);
    assertThat(event.getEventType()).isEqualTo("org.created");
    assertThat(event.getStatus()).isEqualTo(OutboxStatus.READY);
    assertThat(event.getEventUuid()).isNotBlank();
    assertThat(event.getPayload())
        .contains("\"eventType\":\"org.created\"")
        .contains("\"businessKey\":\"" + cd + "\"")
        .contains("\"schemaVersion\":1");

    assertThat(changeLogRepository.findByAggTypeOrderBySeqNoAsc("ORG"))
        .anyMatch(l -> l.getAggId().equals(orgId) && l.getSnapshotJson().contains(cd));
  }

  @Test
  @DisplayName("ChangeLog SEQ_NO는 단조 증가하는 커서다")
  void changeLogSeqNoIsMonotonicCursor() {
    String cd1 = TestSeq.orgCd();
    Long id1 =
        orgService.create(cd1, "조직" + cd1, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1));
    String cd2 = TestSeq.orgCd();
    Long id2 =
        orgService.create(cd2, "조직" + cd2, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1));

    Long seq1 = seqNoOf(id1);
    Long seq2 = seqNoOf(id2);
    assertThat(seq2).isGreaterThan(seq1);
  }

  private Long seqNoOf(Long orgId) {
    return changeLogRepository.findByAggTypeOrderBySeqNoAsc("ORG").stream()
        .filter(l -> l.getAggId().equals(orgId))
        .map(IfChangeLog::getSeqNo)
        .findFirst()
        .orElseThrow();
  }
}
