package com.portfolio.insuhr.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** batch/relay의 flyway validate 전용 모드 (설계서 4.2). */
class FlywayValidateOnlyConfigTest {

  @Test
  @DisplayName("validate 전략은 migrate가 아니라 validate를 호출한다")
  void strategyValidatesInsteadOfMigrating() {
    FlywayMigrationStrategy strategy =
        new FlywayValidateOnlyConfig().validateOnlyMigrationStrategy();
    Flyway flyway = mock(Flyway.class);

    strategy.migrate(flyway);

    verify(flyway).validate();
    // 핵심 단언 — 이 모듈은 스키마를 절대 건드리면 안 된다. migrate 주체는 api 하나다.
    verify(flyway, never()).migrate();
  }

  @Test
  @DisplayName("insuhr.flyway.mode=validate 인 모듈에만 전략이 등록된다")
  void strategyIsRegisteredOnlyForValidateMode() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class))
            .withUserConfiguration(FlywayValidateOnlyConfig.class);

    // batch/relay: 전략이 붙어 validate만 한다
    runner
        .withPropertyValues("insuhr.flyway.mode=validate")
        .run(ctx -> assertThat(ctx).hasSingleBean(FlywayMigrationStrategy.class));

    // api: 속성이 없으므로 부트 기본 동작(migrate)이 유지된다
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(FlywayMigrationStrategy.class));
  }
}
