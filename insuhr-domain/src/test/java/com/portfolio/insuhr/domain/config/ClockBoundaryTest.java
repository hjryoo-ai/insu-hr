package com.portfolio.insuhr.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 시각/날짜 파생 규칙의 앵커 테스트 (설계서 6.2 v1.5).
 *
 * <p>주입된 {@code Clock}에서 저장 시각({@code Instant})과 업무 날짜({@code LocalDate})가 갈린다. 이 둘이 다른 날을 가리키는
 * 새벽(한국시간 00:00~09:00) 구간을 고정해, 규칙이 살아 있는지 지킨다:
 *
 * <ul>
 *   <li>업무 날짜 {@code LocalDate.now(clock)} = KST 달력의 오늘 (8월 1일)
 *   <li>저장 시각 {@code Instant} 의 UTC 날짜 = 아직 7월 31일
 * </ul>
 *
 * <p>이 경계를 Phase 5 기한 판정과 Phase 7 새벽 배치가 밟는다. 누군가 {@code ClockConfig.BUSINESS_ZONE}을 UTC로 되돌리면 이
 * 테스트가 즉시 깨져 규약 위반을 잡는다.
 */
class ClockBoundaryTest {

  // 2026-08-01T00:05+09:00 == 2026-07-31T15:05Z
  private static final Instant DAWN_KST = Instant.parse("2026-07-31T15:05:00Z");

  @Test
  @DisplayName("업무존(KST) clock: 업무 날짜는 8월 1일, 저장 시각의 UTC 날짜는 7월 31일")
  void businessDateIsKstWhileInstantIsStillUtcYesterday() {
    Clock clock = Clock.fixed(DAWN_KST, ClockConfig.BUSINESS_ZONE);

    // 업무 날짜: KST 달력의 오늘.
    assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 8, 1));

    // 같은 절대시각을 UTC로 보면 아직 전날이다 — Instant는 존과 무관하므로 UTC 적재 규약은 흔들리지 않는다.
    assertThat(clock.instant()).isEqualTo(DAWN_KST);
    assertThat(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))
        .isEqualTo(LocalDate.of(2026, 7, 31));
  }

  @Test
  @DisplayName("만약 존이 UTC였다면 업무 날짜가 전날로 어긋난다 — 이 버그를 KST가 막는다")
  void utcZoneWouldMisdateToPreviousDay() {
    Clock utcClock = Clock.fixed(DAWN_KST, ZoneOffset.UTC);

    // 규약을 어겨 UTC로 뽑으면 새벽 배치의 '오늘'이 7월 31일이 되어 8월 1일 도래분을 하루 늦게 반영한다.
    assertThat(LocalDate.now(utcClock)).isEqualTo(LocalDate.of(2026, 7, 31));
    assertThat(LocalDate.now(utcClock))
        .as("KST 업무존과 다른 날 — 이 차이가 하루 어긋남 버그의 정체")
        .isNotEqualTo(LocalDate.now(Clock.fixed(DAWN_KST, ClockConfig.BUSINESS_ZONE)));
  }
}
