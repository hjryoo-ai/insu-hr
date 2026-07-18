package com.portfolio.insuhr.domain.integration;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 변경분 Pull 조회 (설계서 9.4).
 *
 * <p><b>워터마크 지연</b>: Oracle 시퀀스는 채번 순서 ≠ 커밋 순서라, 커서가 지난 뒤 낮은 SEQ_NO가 늦게 커밋되면 영구 유실된다. {@code
 * CHANGED_AT < 현재 - N초}(정책값 {@code SYNC_WATERMARK_SECONDS})인 행만 반환해 그 함정을 막는다 — DB 시계로 비교한다(9.4).
 *
 * <p>{@code CHANGED_AT}는 Oracle 드라이버의 tz 해석(ORA-18716)을 피하려 {@code TO_CHAR}로 UTC ISO 문자열로 뽑는다. 커서는
 * 전역 {@code SEQ_NO} 키셋이라 수신측이 마지막 값만 저장하면 재개된다.
 */
@Repository
public class SyncChangeQueryDao {

  private final JdbcClient jdbcClient;

  public SyncChangeQueryDao(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public List<ChangeRow> findChanges(String aggType, long cursor, int size, int watermarkSeconds) {
    return jdbcClient
        .sql(
            """
            SELECT SEQ_NO AS seqNo,
                   AGG_TYPE AS aggType,
                   AGG_ID AS aggId,
                   CHANGE_TYPE_CD AS changeType,
                   TO_CHAR(CHANGED_AT, 'YYYY-MM-DD"T"HH24:MI:SS.FF3"Z"') AS changedAt,
                   SNAPSHOT_JSON AS snapshotJson
              FROM TB_IF_CHANGE_LOG
             WHERE SEQ_NO > :cursor
               AND (:aggType IS NULL OR AGG_TYPE = :aggType)
               AND CHANGED_AT < SYS_EXTRACT_UTC(SYSTIMESTAMP) - NUMTODSINTERVAL(:watermark, 'SECOND')
             ORDER BY SEQ_NO
             FETCH FIRST :size ROWS ONLY
            """)
        .param("cursor", cursor)
        .param("aggType", aggType)
        .param("watermark", watermarkSeconds)
        .param("size", size)
        .query(ChangeRow.class)
        .list();
  }

  /** 변경 로그 한 행. snapshotJson은 변경 후 전체 상태(state-carried transfer). */
  public record ChangeRow(
      long seqNo,
      String aggType,
      long aggId,
      String changeType,
      String changedAt,
      String snapshotJson) {}
}
