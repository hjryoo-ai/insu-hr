package com.portfolio.insuhr.api.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 변경분 Pull + 워터마크 지연 (설계서 9.4 — 시퀀스 갭 함정 방지).
 *
 * <p>워터마크(기본 5초) 안쪽의 최근 변경은 반환하지 않고, 그보다 오래된 변경만 커서 순으로 돌려준다 — 늦게 커밋되는 낮은 SEQ_NO의 영구 유실을 막는 핵심.
 * snapshot은 전체 상태 JSON으로 실려 수신측이 upsert할 수 있다.
 */
class SyncChangesWatermarkIntegrationTest extends AbstractIntegrationTest {

  @Autowired SyncService syncService;
  @Autowired JdbcClient jdbcClient;

  private long insertChange(String aggType, long aggId, long ageSeconds, String snapshotJson) {
    jdbcClient
        .sql(
            """
            INSERT INTO TB_IF_CHANGE_LOG (SEQ_NO, AGG_TYPE, AGG_ID, CHANGE_TYPE_CD, CHANGED_AT, SNAPSHOT_JSON)
            VALUES (SEQ_IF_CHANGE_LOG.NEXTVAL, :aggType, :aggId, 'U',
                    SYS_EXTRACT_UTC(SYSTIMESTAMP) - NUMTODSINTERVAL(:age, 'SECOND'), :snap)
            """)
        .param("aggType", aggType)
        .param("aggId", aggId)
        .param("age", ageSeconds)
        .param("snap", snapshotJson)
        .update();
    return jdbcClient
        .sql("SELECT SEQ_NO FROM TB_IF_CHANGE_LOG WHERE AGG_TYPE = :t AND AGG_ID = :a")
        .param("t", aggType)
        .param("a", aggId)
        .query(Long.class)
        .single();
  }

  @Test
  @DisplayName("워터마크 안쪽 최근 변경은 숨기고, 오래된 변경만 커서 순으로 반환한다(snapshot은 JSON)")
  void watermarkHidesRecentAndReturnsOldInCursorOrder() {
    String aggType = "ST" + (System.nanoTime() % 1_000_000L); // 테스트 격리용 유니크 aggType
    long oldSeq =
        insertChange(aggType, 1, 60, "{\"agentCd\":\"A1\",\"statusCd\":\"ACTIVE\"}"); // 60초 전
    insertChange(aggType, 2, 0, "{\"agentCd\":\"A2\"}"); // 방금(워터마크 5초 안쪽)

    var response = syncService.changes(aggType, 0, 100);

    // 최근 변경(aggId=2)은 워터마크로 제외, 오래된 변경(aggId=1)만 반환.
    assertThat(response.items()).hasSize(1);
    assertThat(response.items().get(0).seqNo()).isEqualTo(oldSeq);
    assertThat(response.items().get(0).aggId()).isEqualTo(1L);
    // snapshot은 파싱된 JSON — 이중 인코딩 아님.
    assertThat(response.items().get(0).snapshot().get("agentCd").asString()).isEqualTo("A1");
    assertThat(response.nextCursor()).isEqualTo(oldSeq);

    // 커서를 반환된 마지막 seqNo로 올리면 그 이후는 (아직 워터마크 안쪽이라) 비어 있다.
    var next = syncService.changes(aggType, oldSeq, 100);
    assertThat(next.items()).isEmpty();
  }
}
