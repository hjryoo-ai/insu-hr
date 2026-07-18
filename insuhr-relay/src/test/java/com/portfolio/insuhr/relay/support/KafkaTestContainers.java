package com.portfolio.insuhr.relay.support;

import java.time.Duration;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.oracle.OracleContainer;

/**
 * kafka 프로파일 통합 테스트용 공유 컨테이너 (Oracle + Kafka).
 *
 * <p>싱글턴 static 기동 — 스프링 컨텍스트가 클래스 간 캐시되므로 컨테이너도 한 번만 띄운다({@code AbstractRelayIntegrationTest}와 같은
 * 이유). 정리는 JVM 종료 시 Ryuk가 한다. Kafka 이미지는 docker-compose와 같은 {@code apache/kafka}(KRaft 단일 노드).
 */
public final class KafkaTestContainers {

  public static final OracleContainer ORACLE =
      new OracleContainer("gvenzl/oracle-free:23-slim")
          .withUsername("insuhr")
          .withPassword("insuhr")
          .withStartupTimeout(Duration.ofMinutes(5));

  public static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:latest");

  static {
    ORACLE.start();
    KAFKA.start();
  }

  private KafkaTestContainers() {}
}
