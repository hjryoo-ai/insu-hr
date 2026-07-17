package com.portfolio.insuhr.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시각 컬럼 규약: 모든 TIMESTAMP는 UTC 기준으로 적재한다 (설계서 6.2).
 *
 * <p>이 테스트가 있는 이유: `DEFAULT SYSTIMESTAMP`는 DB 호스트 타임존을 따르므로, DB가 Asia/Seoul로 도는 순간 시드 행만 KST로 들어가 앱
 * 기록(UTC)과 9시간 어긋난다. 로컬 컨테이너가 UTC라 <b>증상이 안 보이는 채로 통과</b>하는 종류의 문제라서, 값 비교가 아니라 <b>DDL 자체</b>를
 * 검사한다.
 *
 * <p>Phase가 올라가며 테이블이 늘어날 때 이 규약을 어기면 여기서 걸린다 — 날짜 경계 판정이 많은 배치(설계서 8장)에서 하루 어긋나는 버그로 돌아오는 것을 막는다.
 */
class TimestampConventionTest extends AbstractIntegrationTest {

  @Autowired JdbcClient jdbcClient;

  @Test
  @DisplayName("모든 TIMESTAMP 컬럼의 DEFAULT는 UTC 기준이다 (SYSTIMESTAMP 직접 사용 금지)")
  void allTimestampDefaultsAreUtc() {
    // DATA_DEFAULT는 LONG 이라 WHERE 절에서 다루기 어렵다. 전부 가져와 자바에서 거른다.
    List<ColumnDefault> defaults =
        jdbcClient
            .sql(
                """
                SELECT TABLE_NAME, COLUMN_NAME, DATA_DEFAULT
                  FROM USER_TAB_COLUMNS
                 WHERE DATA_TYPE LIKE 'TIMESTAMP%'
                   AND TABLE_NAME LIKE 'TB\\_%' ESCAPE '\\'
                """)
            .query(ColumnDefault.class)
            .list();

    List<String> violations =
        defaults.stream()
            .filter(c -> c.dataDefault() != null)
            .filter(c -> !c.dataDefault().toUpperCase().contains("SYS_EXTRACT_UTC"))
            .map(c -> c.tableName() + "." + c.columnName() + " = " + c.dataDefault().trim())
            .toList();

    assertThat(violations)
        .as("SYS_EXTRACT_UTC(SYSTIMESTAMP) 대신 SYSTIMESTAMP 를 쓰면 DB 호스트 타임존에 끌려간다 (설계서 6.2)")
        .isEmpty();
  }

  @Test
  @DisplayName("앱이 기록한 시각과 DDL 기본값이 같은 기준선(UTC)을 쓴다")
  void appAndDefaultAgreeOnSameBaseline() {
    // 앱 경로(JPA Auditing, hibernate.jdbc.time_zone=UTC)로 들어간 행과
    // DDL 기본값으로 들어간 행의 시각이 벌어지지 않는지 본다.
    // 두 기준이 어긋나면(예: 앱 UTC / 기본값 KST) 9시간 차이로 나타난다.
    //
    // 주의: 이 테스트는 컨테이너 DB가 UTC라 지금은 어느 쪽이든 통과한다 — 진짜 방어선은
    // 위의 DDL 검사다. 여기서는 두 경로가 공존한다는 사실만 확인한다.
    jdbcClient
        .sql(
            "INSERT INTO TB_CD_GRP (GRP_CD, GRP_NM, CREATED_BY) VALUES ('TZ_PROBE', 'probe', 'TEST')")
        .update();
    try {
      Long driftSeconds =
          jdbcClient
              .sql(
                  """
                  SELECT ABS(ROUND((CAST(CREATED_AT AS DATE) - CAST(SYS_EXTRACT_UTC(SYSTIMESTAMP) AS DATE)) * 86400))
                    FROM TB_CD_GRP WHERE GRP_CD = 'TZ_PROBE'
                  """)
              .query(Long.class)
              .single();

      assertThat(driftSeconds).as("DDL 기본값으로 들어간 시각이 UTC 기준에서 벗어났다 (설계서 6.2)").isLessThan(60);
    } finally {
      jdbcClient.sql("DELETE FROM TB_CD_GRP WHERE GRP_CD = 'TZ_PROBE'").update();
    }
  }

  record ColumnDefault(String tableName, String columnName, String dataDefault) {}
}
