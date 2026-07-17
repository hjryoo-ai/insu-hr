package com.portfolio.insuhr.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/**
 * Phase 0 완료 기준 검증 (설계서 13.2).
 *
 * <p>1) Oracle 컨테이너 기동 2) Flyway 마이그레이션 적용 3) /actuator/health UP
 */
class BootstrapIntegrationTest extends AbstractIntegrationTest {

  @LocalServerPort int port;

  @Autowired JdbcClient jdbcClient;

  @Autowired Flyway flyway;

  @Test
  @DisplayName("/actuator/health 가 UP 이고 DB 컴포넌트도 UP")
  void healthIsUp() {
    String body =
        RestClient.create()
            .get()
            .uri("http://localhost:{port}/actuator/health", port)
            .retrieve()
            .body(String.class);

    assertThat(body).contains("\"status\":\"UP\"");
    // DB 컴포넌트까지 UP 이어야 한다 — 애플리케이션만 뜨고 DB가 죽어도 전체 status는 UP이 아니므로
    // 아래 단언이 사실상 커넥션 검증을 겸한다.
    assertThat(body).contains("\"db\"");
  }

  @Test
  @DisplayName("Flyway가 V1을 적용했다")
  void flywayMigrationApplied() {
    // 이력은 Flyway 빈으로 확인한다. flyway_schema_history 를 직접 SELECT 하지 않는 이유:
    // Flyway 12는 Oracle에서 이 테이블을 소문자 따옴표 식별자로 만들어서, 컬럼을 참조하려면
    // "installed_rank" 처럼 일일이 따옴표를 붙여야 한다. Flyway 내부 스키마에 테스트를 묶을 이유가 없다.
    assertThat(flyway.info().current()).isNotNull();
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    assertThat(flyway.info().applied()).hasSize(1);
  }

  @Test
  @DisplayName("V1이 만든 스키마가 실제로 조회된다")
  void schemaCreatedByMigration() {
    Integer rows =
        jdbcClient.sql("SELECT COUNT(*) FROM TB_SCHEMA_BOOTSTRAP").query(Integer.class).single();
    assertThat(rows).isEqualTo(1);
  }

  @Test
  @DisplayName("접속 계정이 설계서의 로컬 규격과 일치한다")
  void connectsAsInsuhrUser() {
    String user = jdbcClient.sql("SELECT USER FROM DUAL").query(String.class).single();
    assertThat(user).isEqualToIgnoringCase("insuhr");
  }
}
