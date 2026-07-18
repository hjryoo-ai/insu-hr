package com.portfolio.insuhr.domain.integration;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 전달 레코드·Outbox 요약·전송이력 데이터 접근 (설계서 9.2 v1.7/v1.8).
 *
 * <p><b>QueryDao의 쓰기 예외</b>(설계서 4.3 v1.8): 팬아웃은 JPA가 아니라 {@code INSERT ... SELECT ... WHERE NOT
 * EXISTS} 한 문장으로 쓴다. JPA면 {@code UQ(EVENT_ID, SUBSCRIBER_ID)} 위반이 영속성 컨텍스트를 rollback-only로
 * 오염시켜(Phase 2 함정) 같은 트랜잭션 후속이 죽는다. {@code WHERE NOT EXISTS}는 위반을 애초에 안 만들고(UQ는 크래시 백스톱), 필터가 SQL 한
 * 방이라 성능도 낫다.
 *
 * <p>모든 시각 비교·기록은 <b>DB 시계</b>({@code SYS_EXTRACT_UTC(SYSTIMESTAMP)})로 한다 — 앱 시계와 섞으면 지연 창이
 * 흔들린다(9.2). 트랜잭션 경계는 호출자(릴레이 서비스)가 건다.
 */
@Repository
public class IfDeliveryDao {

  private static final int MAX_ERROR_LEN = 1000;

  private final JdbcClient jdbcClient;

  public IfDeliveryDao(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  // ──────────────────────────────── 팬아웃 단계 ────────────────────────────────

  /** READY Outbox를 EVENT_ID 순으로 집는다(소비 대상). */
  public List<ReadyEvent> pickReadyEvents(int limit) {
    return jdbcClient
        .sql(
            """
            SELECT EVENT_ID   AS eventId,
                   AGG_TYPE   AS aggType,
                   AGG_ID     AS aggId,
                   EVENT_TYPE AS eventType
              FROM TB_IF_OUTBOX
             WHERE STATUS_CD = 'READY'
             ORDER BY EVENT_ID
             FETCH FIRST :limit ROWS ONLY
            """)
        .param("limit", limit)
        .query(ReadyEvent.class)
        .list();
  }

  /**
   * 한 이벤트를 활성 <b>푸시 구독자</b>(WEBHOOK·KAFKA, TOPIC_FILTER 매칭)에게 팬아웃한다. 이미 있는 (이벤트,구독자)는 건너뛴다(크래시 재실행
   * 멱등).
   *
   * <p><b>전송 타입 무관 팬아웃</b>(설계서 8 v2.2): DELIVERY_TYPE_CD는 구독자 속성이므로 팬아웃은 타입을 가리지 않고 전달 레코드를 만들고, 전송
   * 단계가 타입별 퍼블리셔로 갈린다. 그래야 KAFKA 구독자도 WEBHOOK과 <b>같은 순서 게이트</b>를 탄다. PULL·FILE은 푸시가 아니라 제외한다(각각 커서
   * API·배치 파일 계층).
   *
   * @return 새로 만든 전달 레코드 수 — 0이면 매칭 구독자 없음(대상 없음)
   */
  public int fanout(long eventId, String aggType, long aggId, String eventType) {
    return jdbcClient
        .sql(
            """
            INSERT INTO TB_IF_DELIVERY (EVENT_ID, SUBSCRIBER_ID, AGG_TYPE, AGG_ID)
            SELECT :eventId, s.SUBSCRIBER_ID, :aggType, :aggId
              FROM TB_IF_SUBSCRIBER s
             WHERE s.USE_YN = 'Y'
               AND s.DELIVERY_TYPE_CD IN ('WEBHOOK', 'KAFKA')
               AND (s.TOPIC_FILTER IS NULL
                    OR JSON_EXISTS(s.TOPIC_FILTER, '$[*]?(@ == $et)' PASSING :eventType AS "et"))
               AND NOT EXISTS (SELECT 1
                                 FROM TB_IF_DELIVERY d
                                WHERE d.EVENT_ID = :eventId
                                  AND d.SUBSCRIBER_ID = s.SUBSCRIBER_ID)
            """)
        .param("eventId", eventId)
        .param("aggType", aggType)
        .param("aggId", aggId)
        .param("eventType", eventType)
        .update();
  }

  /** 팬아웃 완료 표시: READY → FANNED_OUT. */
  public void markOutboxFannedOut(long eventId) {
    jdbcClient
        .sql(
            "UPDATE TB_IF_OUTBOX SET STATUS_CD = 'FANNED_OUT'"
                + " WHERE EVENT_ID = :id AND STATUS_CD = 'READY'")
        .param("id", eventId)
        .update();
  }

  /** 대상 없음(구독자 0명): READY → SENT — 유령 상태 방지(9.2 v1.8). */
  public void markOutboxSentNoTarget(long eventId) {
    jdbcClient
        .sql(
            "UPDATE TB_IF_OUTBOX"
                + " SET STATUS_CD = 'SENT', PUBLISHED_AT = SYS_EXTRACT_UTC(SYSTIMESTAMP)"
                + " WHERE EVENT_ID = :id AND STATUS_CD = 'READY'")
        .param("id", eventId)
        .update();
  }

  // ──────────────────────────────── 전달 단계 ────────────────────────────────

  /** 전송 대상 전달 레코드(PENDING + 재시도 시각 도달, 활성 구독자)를 (구독자, DELIVERY_ID) 순으로 집는다. */
  public List<PendingDelivery> pickPending(int limit) {
    return jdbcClient
        .sql(
            """
            SELECT d.DELIVERY_ID   AS deliveryId,
                   d.EVENT_ID      AS eventId,
                   d.SUBSCRIBER_ID AS subscriberId,
                   d.AGG_TYPE      AS aggType,
                   d.AGG_ID        AS aggId,
                   d.RETRY_CNT     AS retryCnt,
                   o.EVENT_UUID    AS eventUuid,
                   o.EVENT_TYPE    AS eventType,
                   o.PAYLOAD       AS payload,
                   s.SYSTEM_CD        AS systemCd,
                   s.ENDPOINT_URL     AS endpointUrl,
                   s.SECRET_ENC       AS secretEnc,
                   s.DELIVERY_TYPE_CD AS deliveryTypeCd
              FROM TB_IF_DELIVERY d
              JOIN TB_IF_OUTBOX o     ON o.EVENT_ID = d.EVENT_ID
              JOIN TB_IF_SUBSCRIBER s ON s.SUBSCRIBER_ID = d.SUBSCRIBER_ID
             WHERE d.STATUS_CD = 'PENDING'
               AND d.NEXT_RETRY_AT <= SYS_EXTRACT_UTC(SYSTIMESTAMP)
               AND s.USE_YN = 'Y'
             ORDER BY d.SUBSCRIBER_ID, d.DELIVERY_ID
             FETCH FIRST :limit ROWS ONLY
            """)
        .param("limit", limit)
        .query(PendingDelivery.class)
        .list();
  }

  /**
   * 순서 게이트: 같은 (구독자, aggType+aggId)에 이 레코드보다 앞선 <b>미전송(PENDING/FAILED)</b> 선행이 있으면 참(후행 보류).
   *
   * <p>잠금과 무관한 일반 조회다 — 다른 워커가 잠근 선행도 미전송으로 보여야 순서가 지켜진다(9.2 단일 인스턴스 가정).
   */
  public boolean hasUnsentPredecessor(
      long subscriberId, String aggType, long aggId, long deliveryId) {
    Integer count =
        jdbcClient
            .sql(
                """
                SELECT COUNT(*)
                  FROM TB_IF_DELIVERY
                 WHERE SUBSCRIBER_ID = :sub
                   AND AGG_TYPE = :aggType
                   AND AGG_ID = :aggId
                   AND DELIVERY_ID < :deliveryId
                   AND STATUS_CD IN ('PENDING', 'FAILED')
                """)
            .param("sub", subscriberId)
            .param("aggType", aggType)
            .param("aggId", aggId)
            .param("deliveryId", deliveryId)
            .query(Integer.class)
            .single();
    return count != null && count > 0;
  }

  /** 전송 성공: PENDING → SENT(종결). */
  public void markDeliverySent(long deliveryId) {
    jdbcClient
        .sql(
            "UPDATE TB_IF_DELIVERY"
                + " SET STATUS_CD = 'SENT', SENT_AT = SYS_EXTRACT_UTC(SYSTIMESTAMP)"
                + " WHERE DELIVERY_ID = :id")
        .param("id", deliveryId)
        .update();
  }

  /** 전송 실패(재시도 여지): RETRY_CNT++ + NEXT_RETRY_AT = 현재 + 백오프, PENDING 유지. */
  public void markDeliveryRetry(long deliveryId, long backoffSeconds, String lastError) {
    jdbcClient
        .sql(
            """
            UPDATE TB_IF_DELIVERY
               SET RETRY_CNT = RETRY_CNT + 1,
                   NEXT_RETRY_AT = SYS_EXTRACT_UTC(SYSTIMESTAMP) + NUMTODSINTERVAL(:secs, 'SECOND'),
                   LAST_ERROR = :err
             WHERE DELIVERY_ID = :id
            """)
        .param("secs", backoffSeconds)
        .param("err", trimError(lastError))
        .param("id", deliveryId)
        .update();
  }

  /** 재시도 한도 초과: RETRY_CNT++ + PENDING → FAILED(수동 재전송 대상, 후행 보류 유지). */
  public void markDeliveryFailed(long deliveryId, String lastError) {
    jdbcClient
        .sql(
            "UPDATE TB_IF_DELIVERY"
                + " SET RETRY_CNT = RETRY_CNT + 1, STATUS_CD = 'FAILED', LAST_ERROR = :err"
                + " WHERE DELIVERY_ID = :id")
        .param("err", trimError(lastError))
        .param("id", deliveryId)
        .update();
  }

  /** 전송 시도 1건 기록(성공·실패 모두, 설계서 9.2). */
  public void insertSendLog(
      long eventId,
      String systemCd,
      boolean success,
      Integer httpStatus,
      String respBody,
      int retryNo) {
    jdbcClient
        .sql(
            """
            INSERT INTO TB_IF_SEND_LOG (EVENT_ID, SYSTEM_CD, HTTP_STATUS, RESULT_CD, RESP_BODY, RETRY_NO)
            VALUES (:eventId, :systemCd, :httpStatus, :resultCd, :respBody, :retryNo)
            """)
        .param("eventId", eventId)
        .param("systemCd", systemCd)
        .param("httpStatus", httpStatus)
        .param("resultCd", success ? "SUCCESS" : "FAIL")
        .param("respBody", respBody)
        .param("retryNo", retryNo)
        .update();
  }

  /** 이벤트의 전 전달 레코드가 종결(SENT/SKIPPED)인가 — 요약 수렴 판정. */
  public boolean allSiblingsTerminal(long eventId) {
    Integer nonTerminal =
        jdbcClient
            .sql(
                "SELECT COUNT(*) FROM TB_IF_DELIVERY"
                    + " WHERE EVENT_ID = :id AND STATUS_CD IN ('PENDING', 'FAILED')")
            .param("id", eventId)
            .query(Integer.class)
            .single();
    return nonTerminal != null && nonTerminal == 0;
  }

  /** Outbox 요약 수렴: FANNED_OUT → SENT. */
  public void markOutboxSentSummary(long eventId) {
    jdbcClient
        .sql(
            "UPDATE TB_IF_OUTBOX"
                + " SET STATUS_CD = 'SENT', PUBLISHED_AT = SYS_EXTRACT_UTC(SYSTIMESTAMP)"
                + " WHERE EVENT_ID = :id AND STATUS_CD = 'FANNED_OUT'")
        .param("id", eventId)
        .update();
  }

  // ──────────────────────────────── 관리(비활성/재전송) ────────────────────────────────

  /** 구독자 비활성 시 미전송(PENDING/FAILED) 레코드를 SKIPPED로 종결한다. 영향받은 이벤트 ID 목록을 돌려준다(요약 재수렴용). */
  public List<Long> skipUnsentForSubscriber(long subscriberId) {
    List<Long> affected =
        jdbcClient
            .sql(
                "SELECT DISTINCT EVENT_ID FROM TB_IF_DELIVERY"
                    + " WHERE SUBSCRIBER_ID = :sub AND STATUS_CD IN ('PENDING', 'FAILED')")
            .param("sub", subscriberId)
            .query(Long.class)
            .list();
    jdbcClient
        .sql(
            "UPDATE TB_IF_DELIVERY SET STATUS_CD = 'SKIPPED'"
                + " WHERE SUBSCRIBER_ID = :sub AND STATUS_CD IN ('PENDING', 'FAILED')")
        .param("sub", subscriberId)
        .update();
    return affected;
  }

  /** 수동 재전송: 이벤트의 FAILED 전달 레코드를 PENDING으로 되돌린다(재시도 즉시 대상). */
  public int resendFailedForEvent(long eventId) {
    return jdbcClient
        .sql(
            """
            UPDATE TB_IF_DELIVERY
               SET STATUS_CD = 'PENDING',
                   RETRY_CNT = 0,
                   NEXT_RETRY_AT = SYS_EXTRACT_UTC(SYSTIMESTAMP),
                   LAST_ERROR = NULL
             WHERE EVENT_ID = :id AND STATUS_CD = 'FAILED'
            """)
        .param("id", eventId)
        .update();
  }

  private static String trimError(String s) {
    if (s == null) {
      return null;
    }
    return s.length() > MAX_ERROR_LEN ? s.substring(0, MAX_ERROR_LEN) : s;
  }

  /** 팬아웃 대상 READY 이벤트. */
  public record ReadyEvent(long eventId, String aggType, long aggId, String eventType) {}

  /** 전송 대상 전달 레코드(+발송에 필요한 이벤트·구독자 정보). {@code deliveryTypeCd}로 퍼블리셔를 고른다(WEBHOOK/KAFKA). */
  public record PendingDelivery(
      long deliveryId,
      long eventId,
      long subscriberId,
      String aggType,
      long aggId,
      int retryCnt,
      String eventUuid,
      String eventType,
      String payload,
      String systemCd,
      String endpointUrl,
      String secretEnc,
      String deliveryTypeCd) {}
}
