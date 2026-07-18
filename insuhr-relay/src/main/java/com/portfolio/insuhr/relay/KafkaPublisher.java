package com.portfolio.insuhr.relay;

import com.portfolio.insuhr.domain.integration.IfDeliveryDao.PendingDelivery;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 전송기 (설계서 9.1, 8 v2.2 Phase 8). {@code kafka} 프로파일에서만 등록된다({@link Profile}).
 *
 * <p><b>key = {@code aggType:aggId}</b>: 같은 애그리거트의 이벤트가 항상 같은 파티션에 들어가 파티션 내 순서가 보장된다 — 웹훅 순서 게이트가
 * {@code (구독자, aggType, aggId)}로 주는 보장과 정확히 대응한다({@code aggId}만으로는 애그리거트 타입 간 충돌 가능). 값은 저장된
 * payload 문자열 그대로(재직렬화 금지, 9.2). {@code eventUuid}는 수신측 멱등키로 헤더에 싣는다.
 *
 * <p>전송은 동기 확인한다(프로듀서 {@code acks} 설정에 따름) — 결과 판정을 {@link RelayService}가 반영하려면 성공/실패가 확정돼야 한다.
 * {@code WEBHOOK}과 달리 HTTP 상태가 없으므로 {@code PublishResult.httpStatus}는 null이다.
 */
@Component
@Profile("kafka")
public class KafkaPublisher implements EventPublisher {

  static final String HEADER_EVENT_UUID = "X-InsuHR-Event-Uuid";
  private static final long SEND_TIMEOUT_SECONDS = 10;

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String topic;

  public KafkaPublisher(
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${insuhr.relay.kafka.topic:insuhr.events}") String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  @Override
  public String deliveryType() {
    return "KAFKA";
  }

  @Override
  public PublishResult publish(PendingDelivery delivery) {
    String key = delivery.aggType() + ":" + delivery.aggId();
    ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, delivery.payload());
    record.headers().add(HEADER_EVENT_UUID, delivery.eventUuid().getBytes(StandardCharsets.UTF_8));
    try {
      kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return PublishResult.success(null, null);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return PublishResult.failure(null, "전송 중단: " + e.getMessage());
    } catch (Exception e) {
      return PublishResult.failure(null, e.getMessage());
    }
  }
}
