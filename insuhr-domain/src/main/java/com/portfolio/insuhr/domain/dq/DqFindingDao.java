package com.portfolio.insuhr.domain.dq;

import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 정합성 점검 리포트 적재 (설계서 8 v2.0, Phase 7 후속).
 *
 * <p>알림 큐와 같은 {@code INSERT … SELECT … WHERE NOT EXISTS} 멱등 적재 envelope을 제공한다 — 다만 상태를 바꾸지 않고
 * <b>관측만</b> 한다(이벤트 발행도 없다, 알림과 다른 점). 각 잡은 {@link DqRule} 목록만 다르게 들고, 이 envelope에 룰의 대상 SELECT를 끼워
 * 넣는다.
 *
 * <p>대상 SELECT가 내는 {@code DETAIL}은 {@code JSON_OBJECT(...)}로 만든 유효 JSON이어야 한다({@code
 * CK_DQ_DETAIL_JSON}). 감사 {@code CREATED_BY}는 JPA 경로가 아니므로 여기서 {@code 'BATCH'}로 명시한다.
 *
 * <p>트랜잭션 경계는 호출자(배치 스텝/태스클릿)가 건다.
 */
@Repository
public class DqFindingDao {

  private final JdbcClient jdbcClient;

  public DqFindingDao(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * 룰 하나를 평가해 발견분을 멱등 적재한다. 같은 {@code (룰,대상,일자)}는 다시 넣지 않는다({@code UQ_DQ_FINDING}).
   *
   * @param rule 룰 코드·대상종류·대상 SELECT
   * @param foundDt 점검 기준일(잡 {@code targetDate}) — dedup 키의 일부이자 룰 SELECT의 {@code :foundDt} 바인드
   * @return 새로 적재한 발견 건수
   */
  public int runRule(DqRule rule, LocalDate foundDt) {
    String sql =
        """
        INSERT INTO TB_DQ_FINDING (RULE_CD, TARGET_TYPE_CD, TARGET_ID, DETAIL, FOUND_DT, CREATED_BY)
        SELECT :ruleCd, :targetType, t.TARGET_ID, t.DETAIL, :foundDt, 'BATCH'
          FROM ( %s ) t
         WHERE NOT EXISTS (SELECT 1
                             FROM TB_DQ_FINDING f
                            WHERE f.RULE_CD = :ruleCd
                              AND f.TARGET_ID = t.TARGET_ID
                              AND f.FOUND_DT = :foundDt)
        """
            .formatted(rule.targetSelectSql());
    return jdbcClient
        .sql(sql)
        .param("ruleCd", rule.ruleCd())
        .param("targetType", rule.targetTypeCd())
        .param("foundDt", foundDt)
        .update();
  }
}
