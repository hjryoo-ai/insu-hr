package com.portfolio.insuhr.domain.org;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 조직 조회 전용 DAO (설계서 4.3의 QueryDao 규칙 — JdbcClient + 네이티브 SQL, 엔티티가 아닌 전용 record 반환).
 *
 * <p>시점 조회가 JPA로 풀기 어려운 형태(윈도우 함수 + JSON 추출)라 여기 있다.
 */
@Repository
public class OrgQueryDao {

  private final JdbcClient jdbcClient;

  public OrgQueryDao(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * 기준일자 시점의 조직 목록 (설계서 7.2 {@code GET /orgs/tree?asOfDate=}).
   *
   * <p>이력 행이 <b>변경 후 전체 스냅샷</b>을 담는다는 전제(설계서 6.6 v1.2) 덕분에 diff 재생 없이 한 방에 복원된다 — 조직별로 기준일 이전 마지막
   * 이력 1건을 골라 그 스냅샷을 읽으면 그 시점의 상태다.
   *
   * <p>폐지 조직이 자동으로 빠지는 이유: 폐지 이력의 스냅샷은 {@code useYn='N'}이다. 기준일이 폐지일 이후면 그 이력이 '마지막 1건'으로 잡혀 걸러지고,
   * 폐지일 이전이면 그 앞의 이력('Y')이 잡혀 살아난다. 별도 분기가 필요 없다.
   */
  public List<OrgSnapshot> findTreeAsOf(LocalDate asOfDate, String orgTypeCd) {
    return jdbcClient
        .sql(
            """
            SELECT ORG_ID                                            AS orgId,
                   JSON_VALUE(AFTER_JSON, '$.orgCd')                 AS orgCd,
                   JSON_VALUE(AFTER_JSON, '$.orgNm')                 AS orgNm,
                   JSON_VALUE(AFTER_JSON, '$.orgTypeCd')             AS orgTypeCd,
                   JSON_VALUE(AFTER_JSON, '$.upOrgId' RETURNING NUMBER) AS upOrgId,
                   JSON_VALUE(AFTER_JSON, '$.orgLvl'  RETURNING NUMBER) AS orgLvl,
                   JSON_VALUE(AFTER_JSON, '$.sortOrd' RETURNING NUMBER) AS sortOrd
              FROM (
                    SELECT ORG_ID,
                           AFTER_JSON,
                           ROW_NUMBER() OVER (
                             PARTITION BY ORG_ID
                             ORDER BY EFFECTIVE_DT DESC, ORG_HIST_ID DESC
                           ) AS RN
                      FROM TB_ORG_HIST
                     WHERE EFFECTIVE_DT <= :asOfDate
                   )
             WHERE RN = 1
               AND JSON_VALUE(AFTER_JSON, '$.useYn') = 'Y'
               AND (:orgTypeCd IS NULL OR JSON_VALUE(AFTER_JSON, '$.orgTypeCd') = :orgTypeCd)
             ORDER BY orgLvl, sortOrd, orgCd
            """)
        .param("asOfDate", Date.valueOf(asOfDate))
        .param("orgTypeCd", orgTypeCd)
        .query(OrgSnapshot.class)
        .list();
  }

  /**
   * 조직 하위 트리의 ID 목록 (자기 자신 포함).
   *
   * <p>Oracle 계층 질의({@code CONNECT BY})를 쓴다. 설계서 10.1의 행 수준 접근통제(BRANCH_MANAGER는 소속 조직 트리 하위만)와 조직
   * 폐지 판정이 이 목록을 필요로 한다.
   */
  public List<Long> findSubTreeIds(Long rootOrgId) {
    return jdbcClient
        .sql(
            """
            SELECT ORG_ID
              FROM TB_ORG
             START WITH ORG_ID = :rootOrgId
             CONNECT BY PRIOR ORG_ID = UP_ORG_ID
            """)
        .param("rootOrgId", rootOrgId)
        .query(Long.class)
        .list();
  }

  /**
   * 기준일 시점의 조직 스냅샷 1행.
   *
   * @param upOrgId 루트면 null
   */
  public record OrgSnapshot(
      Long orgId,
      String orgCd,
      String orgNm,
      String orgTypeCd,
      Long upOrgId,
      Integer orgLvl,
      Integer sortOrd) {}
}
