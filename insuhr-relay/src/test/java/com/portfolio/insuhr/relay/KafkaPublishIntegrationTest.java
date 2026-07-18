package com.portfolio.insuhr.relay;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.relay.support.KafkaTestContainers;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * kafka 프로파일 전송 — KAFKA 구독자가 같은 팬아웃·순서 게이트를 타고 key=aggType:aggId로 발행됨 (설계서 8 v2.2 Phase 8).
 *
 * <p>완료 기준의 "kafka 프로파일 통합 테스트 1건". 기본 프로파일 테스트들은 이 프로파일을 켜지 않아 Kafka 없이 그대로 그린이다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "insuhr.flyway.mode=migrate",
      "insuhr.relay.scheduler.enabled=false",
      "insuhr.relay.batch-size=100"
    })
@ActiveProfiles("kafka")
@Tag("integration")
class KafkaPublishIntegrationTest {

  private static final String TOPIC = "insuhr.events";

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", KafkaTestContainers.ORACLE::getJdbcUrl);
    registry.add("spring.datasource.username", KafkaTestContainers.ORACLE::getUsername);
    registry.add("spring.datasource.password", KafkaTestContainers.ORACLE::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KafkaTestContainers.KAFKA::getBootstrapServers);
  }

  @Autowired JdbcClient jdbcClient;
  @Autowired RelayPoller poller;

  @Test
  @DisplayName("KAFKA 구독자는 팬아웃→전달 게이트를 타고 key=aggType:aggId로 Kafka에 발행되고 Outbox는 SENT로 수렴한다")
  void kafkaSubscriberReceivesKeyedEvent() {
    long subscriberId = seedKafkaSubscriber();
    long eventId = seedOutboxEvent("AGENT", 1024, "agent.appointed", "kafka-marker");

    try (KafkaConsumer<String, String> consumer = consumer()) {
      consumer.subscribe(List.of(TOPIC));

      poller.runOnce(); // 팬아웃(KAFKA 전달 레코드 생성) → 전달(KafkaPublisher로 라우팅)

      ConsumerRecord<String, String> record = poll(consumer);
      assertThat(record).as("Kafka 토픽에 이벤트가 도착").isNotNull();
      assertThat(record.key()).as("파티션 순서 키 = aggType:aggId").isEqualTo("AGENT:1024");
      assertThat(record.value()).contains("kafka-marker");
      assertThat(new String(record.headers().lastHeader("X-InsuHR-Event-Uuid").value()))
          .isNotBlank();
    }

    assertThat(deliveryStatus(eventId, subscriberId)).as("전달 성공").isEqualTo("SENT");
    assertThat(outboxStatus(eventId)).as("형제 전부 종결 → Outbox 요약 SENT").isEqualTo("SENT");
  }

  private ConsumerRecord<String, String> poll(KafkaConsumer<String, String> consumer) {
    long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
    while (System.nanoTime() < deadline) {
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, String> r : records) {
        if (TOPIC.equals(r.topic())) {
          return r;
        }
      }
    }
    return null;
  }

  private KafkaConsumer<String, String> consumer() {
    Map<String, Object> props =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KafkaTestContainers.KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "test-" + System.nanoTime(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    return new KafkaConsumer<>(props);
  }

  private long seedKafkaSubscriber() {
    String cd = "KAFKA_SYS" + (System.nanoTime() % 100_000_000L);
    jdbcClient
        .sql(
            "INSERT INTO TB_IF_SUBSCRIBER (SYSTEM_CD, SYSTEM_NM, DELIVERY_TYPE_CD, USE_YN,"
                + " CREATED_BY) VALUES (:cd, '카프카시스템', 'KAFKA', 'Y', 'TEST')")
        .param("cd", cd)
        .update();
    return jdbcClient
        .sql("SELECT SUBSCRIBER_ID FROM TB_IF_SUBSCRIBER WHERE SYSTEM_CD = :cd")
        .param("cd", cd)
        .query(Long.class)
        .single();
  }

  private long seedOutboxEvent(String aggType, long aggId, String eventType, String marker) {
    String uuid = "evt-" + (System.nanoTime() % 100_000_000L);
    String payload =
        "{\"eventUuid\":\""
            + uuid
            + "\",\"eventType\":\""
            + eventType
            + "\",\"marker\":\""
            + marker
            + "\",\"schemaVersion\":1}";
    jdbcClient
        .sql(
            "INSERT INTO TB_IF_OUTBOX (EVENT_UUID, AGG_TYPE, AGG_ID, EVENT_TYPE, PAYLOAD, STATUS_CD)"
                + " VALUES (:uuid, :aggType, :aggId, :eventType, :payload, 'READY')")
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

  private String deliveryStatus(long eventId, long subscriberId) {
    return jdbcClient
        .sql("SELECT STATUS_CD FROM TB_IF_DELIVERY WHERE EVENT_ID = :e AND SUBSCRIBER_ID = :s")
        .param("e", eventId)
        .param("s", subscriberId)
        .query(String.class)
        .single();
  }

  private String outboxStatus(long eventId) {
    return jdbcClient
        .sql("SELECT STATUS_CD FROM TB_IF_OUTBOX WHERE EVENT_ID = :id")
        .param("id", eventId)
        .query(String.class)
        .single();
  }
}
