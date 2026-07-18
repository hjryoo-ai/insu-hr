package com.portfolio.insuhr.relay;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Kafka 프로듀서 배선 (설계서 8 v2.2 Phase 8). {@code kafka} 프로파일에서만.
 *
 * <p><b>타입 있는 {@code KafkaTemplate<String, String>}을 직접 정의</b>하는 이유: Boot 자동설정의 기본 템플릿은 {@code
 * <Object, Object>}라 {@link KafkaPublisher}의 {@code <String, String>} 주입점과 제네릭이 안 맞는다. 여기서 String
 * 직렬화 팩토리를 명시해 주입을 확정한다. {@code acks=all}로 리더+ISR 확인 후 성공 처리(전달 반영이 확정 성공에 의존).
 */
@Configuration
@Profile("kafka")
public class KafkaRelayConfig {

  @Bean
  public ProducerFactory<String, String> relayProducerFactory(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    return new DefaultKafkaProducerFactory<>(props);
  }

  @Bean
  public KafkaTemplate<String, String> relayKafkaTemplate(
      ProducerFactory<String, String> relayProducerFactory) {
    return new KafkaTemplate<>(relayProducerFactory);
  }
}
