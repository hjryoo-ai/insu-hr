package com.portfolio.insuhr.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/** 기동·스키마 부트스트랩 검증 (설계서 13.2 Phase 0 완료 기준). */
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
  @DisplayName("가용한 마이그레이션이 빠짐없이 적용됐고 실패·미적용이 없다")
  void allMigrationsApplied() {
    // 버전 번호를 박아두지 않는다 — Phase가 올라갈 때마다 이 테스트를 고치게 되고,
    // 그러면 "고치다 보니 통과"하는 테스트가 된다. 확인할 건 "빠진 것도 실패한 것도 없다"이다.
    //
    // Flyway 내부 테이블을 직접 SELECT 하지 않는 이유: Flyway 12는 Oracle에서 이 테이블을
    // 소문자 따옴표 식별자로 만들어 컬럼마다 "installed_rank" 처럼 따옴표가 필요하다(설계서 3.0).
    assertThat(flyway.info().current()).isNotNull();
    assertThat(flyway.info().pending()).isEmpty();
    assertThat(flyway.info().applied())
        .isNotEmpty()
        .allSatisfy(m -> assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS));
  }

  @Test
  @DisplayName("접속 계정이 설계서의 로컬 규격과 일치한다")
  void connectsAsInsuhrUser() {
    String user = jdbcClient.sql("SELECT USER FROM DUAL").query(String.class).single();
    assertThat(user).isEqualToIgnoringCase("insuhr");
  }
}
