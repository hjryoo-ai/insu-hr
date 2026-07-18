package com.portfolio.insuhr.domain.person;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 파기 부수 쓰기 — 주소 삭제 + 파기 대장 적재 (설계서 8 Phase 8).
 *
 * <p>인물 본체 익명화는 {@link Person#anonymize} JPA 경로지만, 주소({@code TB_PERSON_ADDR})는 <b>암호화된 주소를 든 개인정보라
 * 파기해야 하고 {@code ADDR_ENC}가 NOT NULL이라 NULL 치환이 불가</b>하므로 행을 삭제한다(주소는 어디서도 참조되지 않아 삭제가 안전). 대장 적재도
 * 여기 JdbcClient로 — 감사 {@code CREATED_BY}는 배치 폴백과 같은 {@code SYSTEM-insuhr-batch}로 명시해 대장과 인물 {@code
 * UPDATED_BY}가 이중 증적이 되게 한다.
 *
 * <p>트랜잭션 경계는 호출자({@link PersonPurgeService})가 건다.
 */
@Repository
public class PrivacyPurgeDao {

  private static final String BATCH_ACTOR = "SYSTEM-insuhr-batch";

  private final JdbcClient jdbcClient;

  public PrivacyPurgeDao(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /** 인물의 주소 이력을 삭제한다(암호화 주소 파기). */
  public int deleteAddresses(long personId) {
    return jdbcClient
        .sql("DELETE FROM TB_PERSON_ADDR WHERE PERSON_ID = :p")
        .param("p", personId)
        .update();
  }

  /** 파기 대장 1행 적재. {@code UQ_PURGE_LEDGER_PERSON}이 재실행 백스톱. */
  public void insertLedger(long personId, String purgeTypeCd, String eligibleBasisJson) {
    jdbcClient
        .sql(
            """
            INSERT INTO TB_PRIVACY_PURGE_LEDGER (PERSON_ID, PURGE_TYPE_CD, ELIGIBLE_BASIS, CREATED_BY)
            VALUES (:p, :type, :basis, :actor)
            """)
        .param("p", personId)
        .param("type", purgeTypeCd)
        .param("basis", eligibleBasisJson)
        .param("actor", BATCH_ACTOR)
        .update();
  }
}
