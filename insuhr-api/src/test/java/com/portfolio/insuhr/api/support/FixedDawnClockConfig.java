package com.portfolio.insuhr.api.support;

import com.portfolio.insuhr.domain.config.ClockConfig;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 새벽 경계에 고정된 시계 — 시각/날짜 파생 규칙의 통합 앵커 (설계서 6.2 v1.5).
 *
 * <p>{@code 2026-08-01T00:05+09:00}(= UTC로는 {@code 2026-07-31T15:05Z})에 멈춘다. 이 시점에 업무 날짜는 8월 1일이고
 * 저장 시각의 UTC 날짜는 7월 31일이라, "UTC로는 아직 어제지만 8월 1일자 발령이 반영된다"를 통합 경로로 증명할 수 있다.
 *
 * <p>빈 이름을 {@code clock}으로 두지 않는다 — {@code ClockConfig.clock()}과 겹치면 정의 오버라이드가 꺼져 있어 컨텍스트가 안 뜬다(설계서
 * 6.2, TestClockConfig와 같은 이유).
 */
@TestConfiguration
public class FixedDawnClockConfig {

  /** 2026-08-01T00:05+09:00. */
  public static final Instant DAWN = Instant.parse("2026-07-31T15:05:00Z");

  @Bean
  @Primary
  public Clock dawnClock() {
    return Clock.fixed(DAWN, ClockConfig.BUSINESS_ZONE);
  }
}
