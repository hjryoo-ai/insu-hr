package com.portfolio.insuhr.domain.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * migrate 없이 스키마 검증만 수행 (설계서 4.2).
 *
 * <p>migrate 주체는 insuhr-api 하나로 고정한다. 그렇다고 batch/relay에서 Flyway를 꺼버리면, api보다 batch가 먼저 뜨는 배포 순서 실수를
 * 한참 뒤 런타임 SQL 에러로야 알게 된다. validate로 켜두면 스키마 버전이 어긋나는 순간 기동 자체가 실패한다.
 *
 * <p>{@code insuhr.flyway.mode=validate} 인 모듈에만 적용된다. 값이 없으면 부트 기본 동작(migrate).
 */
@Configuration
@ConditionalOnProperty(name = "insuhr.flyway.mode", havingValue = "validate")
public class FlywayValidateOnlyConfig {

  @Bean
  public FlywayMigrationStrategy validateOnlyMigrationStrategy() {
    // 미적용 마이그레이션이 있거나 이력이 비어 있으면 예외를 던진다.
    return flyway -> flyway.validate();
  }
}
