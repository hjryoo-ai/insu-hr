package com.portfolio.insuhr.api.support;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

/**
 * 통합 테스트 공통 베이스 (설계서 12장).
 *
 * <p>H2 같은 대체 DB를 쓰지 않고 실제 Oracle 컨테이너 + Flyway로 돌린다. Oracle 전용 문법(IDENTITY, CONNECT BY, INTERVAL
 * 파티션, OFFSET/FETCH)에 의존하는 설계라서, 다른 DB로 검증하면 통과해도 의미가 없다.
 *
 * <p>컨테이너는 static — 이 클래스를 상속한 테스트들이 하나의 컨테이너와 캐시된 스프링 컨텍스트를 공유한다. Oracle 기동이 수십 초 걸리므로 클래스마다 새로
 * 띄우면 테스트가 못 쓸 만큼 느려진다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
public abstract class AbstractIntegrationTest {

  @Container @ServiceConnection
  static final OracleContainer ORACLE =
      new OracleContainer("gvenzl/oracle-free:23-slim")
          .withUsername("insuhr")
          .withPassword("insuhr")
          // Oracle 23ai Free 는 첫 기동에 시간이 걸린다. TC 기본 타임아웃으로는 부족.
          .withStartupTimeout(Duration.ofMinutes(5));
}
