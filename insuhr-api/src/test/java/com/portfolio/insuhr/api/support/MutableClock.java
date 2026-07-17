package com.portfolio.insuhr.api.support;

import com.portfolio.insuhr.domain.config.ClockConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 테스트에서 "오늘"을 옮길 수 있는 시계.
 *
 * <p>발령 반영 규칙(설계서 5.5, 시나리오 6a)은 날짜 경계가 전부다 — "발령일이 오늘인 건은 반영, 내일 건은 미반영". 시스템 날짜에 의존하면 이 경계를 테스트할
 * 수 없어서, 도메인이 {@code Clock}을 주입받게 하고(설계서 13.2 Phase 3) 테스트는 이 시계로 원하는 날짜에 선다.
 *
 * <p>싱글턴 컨텍스트가 캐시돼 {@code AppointmentApplyService}가 이 인스턴스를 한 번만 붙잡으므로, 내부 instant를 바꾸면 이미 주입된 서비스도
 * 같은 변화를 본다.
 */
public class MutableClock extends Clock {

  private final ZoneId zone = ClockConfig.BUSINESS_ZONE;
  private volatile Instant instant;

  public MutableClock(LocalDate initialDate) {
    setDate(initialDate);
  }

  /** 업무존 기준 그 날 정오로 맞춘다 — 자정 근처 경계 실수를 테스트가 우연히 통과시키지 않도록. */
  public void setDate(LocalDate date) {
    this.instant = ZonedDateTime.of(date, LocalTime.NOON, zone).toInstant();
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("테스트 시계는 존을 바꾸지 않는다");
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
