package com.portfolio.insuhr.api.support;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;

/**
 * 통합 테스트 공통 베이스 (설계서 12장).
 *
 * <p>H2 같은 대체 DB를 쓰지 않고 실제 Oracle 컨테이너 + Flyway로 돌린다. Oracle 전용 문법(IDENTITY, CONNECT BY, INTERVAL
 * 파티션, OFFSET/FETCH)에 의존하는 설계라서, 다른 DB로 검증하면 통과해도 의미가 없다.
 *
 * <p><b>싱글턴 컨테이너 패턴</b> — {@code @Testcontainers} + {@code @Container}를 쓰지 않는다. 그 조합은 컨테이너를 <i>테스트
 * 클래스 단위</i>로 띄우고 내리는데, 스프링 컨텍스트는 클래스 간에 캐시돼 재사용된다. 그래서 첫 클래스가 끝나며 컨테이너를 내리면, 다음 클래스는 캐시된 컨텍스트의 죽은
 * 커넥션 풀을 물고 ORA-17008(Closed connection)로 깨진다.
 *
 * <p>대신 static 초기화로 한 번 띄우고 명시적으로 내리지 않는다. 정리는 JVM 종료 시 Testcontainers의 Ryuk가 한다. Oracle 기동이 수십 초
 * 걸리므로 클래스마다 새로 띄우는 것은 어차피 감당할 수 없다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
public abstract class AbstractIntegrationTest {

  private static final OracleContainer ORACLE =
      new OracleContainer("gvenzl/oracle-free:23-slim")
          .withUsername("insuhr")
          .withPassword("insuhr")
          // Oracle 23ai Free 는 첫 기동에 시간이 걸린다. TC 기본 타임아웃으로는 부족.
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
}
