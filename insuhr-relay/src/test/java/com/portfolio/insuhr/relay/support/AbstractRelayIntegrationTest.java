package com.portfolio.insuhr.relay.support;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.portfolio.insuhr.domain.integration.IfSubscriber;
import com.portfolio.insuhr.domain.integration.IfSubscriberService;
import com.portfolio.insuhr.relay.RelayPoller;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;

/**
 * 릴레이 통합 테스트 공통 베이스 (설계서 9.2, 12장).
 *
 * <p>실제 Oracle + Flyway로 돈다. 릴레이는 운영에선 validate 전용이지만(4.2), 테스트는 {@code insuhr.flyway.mode}를 켜지 않아
 * 빈 컨테이너에 <b>migrate</b>로 스키마를 구성한다(FlywayValidateOnlyConfig는 그 값이 있을 때만 활성).
 *
 * <p>스케줄러는 끄고({@code scheduler.enabled=false}) {@link RelayPoller#runOnce()}를 직접 호출해 결정적으로 검증한다.
 * 재시도 백오프 기저는 0으로 둬 다음 폴에서 즉시 재시도된다(시각 대기 없이 실패→재시도를 검증).
 *
 * <p>웹훅 수신은 WireMock 목이 받는다 — 매 테스트 동적 포트로 띄우고 내린다. 컨테이너는 클래스 간 공유되므로 데이터는 유니크 키로 격리한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      // 운영 릴레이는 validate 전용이지만(application.yml) 테스트는 빈 컨테이너에 migrate로 스키마를 구성한다.
      // FlywayValidateOnlyConfig는 havingValue="validate"라 이 값을 바꾸면 비활성 → Boot 기본 migrate.
      "insuhr.flyway.mode=migrate",
      "insuhr.relay.scheduler.enabled=false",
      "insuhr.relay.retry-base-seconds=0",
      "insuhr.relay.batch-size=100"
    })
@Tag("integration")
public abstract class AbstractRelayIntegrationTest {

  private static final OracleContainer ORACLE =
      new OracleContainer("gvenzl/oracle-free:23-slim")
          .withUsername("insuhr")
          .withPassword("insuhr")
          .withStartupTimeout(Duration.ofMinutes(5));

  static {
    ORACLE.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
    registry.add("spring.datasource.username", ORACLE::getUsername);
    registry.add("spring.datasource.password", ORACLE::getPassword);
  }

  @Autowired protected JdbcClient jdbcClient;
  @Autowired protected IfSubscriberService subscriberService;
  @Autowired protected RelayPoller poller;

  protected WireMockServer wireMock;

  @BeforeEach
  void resetAndStartWireMock() {
    // 컨테이너는 릴레이 테스트 클래스들 사이에서 공유된다. 이전 테스트가 남긴 구독자(특히 전체수신
    // 필터)가 살아 있으면 이번 이벤트가 그 죽은 구독자에게도 팬아웃돼 요약이 수렴하지 못한다.
    // 연계 테이블을 매 테스트 초기화해 팬아웃·요약을 결정적으로 만든다(자식 FK부터 삭제).
    jdbcClient.sql("DELETE FROM TB_IF_SEND_LOG").update();
    jdbcClient.sql("DELETE FROM TB_IF_DELIVERY").update();
    jdbcClient.sql("DELETE FROM TB_IF_OUTBOX").update();
    jdbcClient.sql("DELETE FROM TB_IF_SUBSCRIBER").update();

    wireMock = new WireMockServer(options().dynamicPort());
    wireMock.start();
  }

  @AfterEach
  void stopWireMock() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  protected String webhookUrl(String path) {
    return wireMock.baseUrl() + path;
  }

  /** 테스트 간 겹치지 않는 접미사(컨테이너 공유로 데이터 누적). */
  protected String uniq() {
    return Long.toString(System.nanoTime() % 100_000_000L);
  }

  /** READY Outbox 이벤트 1건을 직접 넣고 EVENT_ID를 돌려준다(팬아웃/전송의 입력). PAYLOAD는 CHECK(IS JSON) 통과용 유효 JSON. */
  protected long insertOutboxEvent(String aggType, long aggId, String eventType, String marker) {
    String uuid = "evt-" + uniq();
    String payload =
        "{\"eventUuid\":\""
            + uuid
            + "\",\"eventType\":\""
            + eventType
            + "\",\"aggregate\":{\"type\":\""
            + aggType
            + "\",\"id\":"
            + aggId
            + "},\"marker\":\""
            + marker
            + "\",\"schemaVersion\":1}";
    jdbcClient
        .sql(
            """
            INSERT INTO TB_IF_OUTBOX (EVENT_UUID, AGG_TYPE, AGG_ID, EVENT_TYPE, PAYLOAD, STATUS_CD)
            VALUES (:uuid, :aggType, :aggId, :eventType, :payload, 'READY')
            """)
        .param("uuid", uuid)
        .param("aggType", aggType)
        .param("aggId", aggId)
        .param("eventType", eventType)
        .param("payload", payload)
        .update();
    return jdbcClient
        .sql("SELECT EVENT_ID FROM TB_IF_OUTBOX WHERE EVENT_UUID = :uuid")
        .param("uuid", uuid)
        .query(Long.class)
        .single();
  }

  /** WEBHOOK 구독자를 만든다. topicFilterJson=null 이면 전체 이벤트 수신. */
  protected IfSubscriber createWebhookSubscriber(
      String systemCd, String endpointPath, String topicFilterJson) {
    return subscriberService.create(
        systemCd,
        systemCd + " 시스템",
        "WEBHOOK",
        webhookUrl(endpointPath),
        "sign-secret-" + systemCd,
        topicFilterJson);
  }

  protected String outboxStatus(long eventId) {
    return jdbcClient
        .sql("SELECT STATUS_CD FROM TB_IF_OUTBOX WHERE EVENT_ID = :id")
        .param("id", eventId)
        .query(String.class)
        .single();
  }

  protected String deliveryStatus(long eventId, long subscriberId) {
    return jdbcClient
        .sql("SELECT STATUS_CD FROM TB_IF_DELIVERY WHERE EVENT_ID = :e AND SUBSCRIBER_ID = :s")
        .param("e", eventId)
        .param("s", subscriberId)
        .query(String.class)
        .single();
  }

  protected int retryCnt(long eventId, long subscriberId) {
    return jdbcClient
        .sql("SELECT RETRY_CNT FROM TB_IF_DELIVERY WHERE EVENT_ID = :e AND SUBSCRIBER_ID = :s")
        .param("e", eventId)
        .param("s", subscriberId)
        .query(Integer.class)
        .single();
  }

  protected List<Map<String, Object>> sendLogs(long eventId) {
    return jdbcClient
        .sql(
            "SELECT RESULT_CD, RETRY_NO, HTTP_STATUS FROM TB_IF_SEND_LOG"
                + " WHERE EVENT_ID = :id ORDER BY SEND_LOG_ID")
        .param("id", eventId)
        .query()
        .listOfRows();
  }
}
