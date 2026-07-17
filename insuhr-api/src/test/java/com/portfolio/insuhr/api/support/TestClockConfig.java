package com.portfolio.insuhr.api.support;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 발령 반영 규칙 테스트용 시계 주입 (설계서 시나리오 6a).
 *
 * <p>{@link MutableClock}을 {@code @Primary}로 올려 실제 {@code ClockConfig.clock()} 대신 주입되게 한다. 이 설정을
 * {@code @Import}한 테스트만 별도 컨텍스트를 갖고, 나머지 테스트는 실제 시계를 그대로 쓴다.
 */
@TestConfiguration
public class TestClockConfig {

  /**
   * 초기 날짜는 아무 고정값이면 된다 — 테스트가 setDate로 옮긴다.
   *
   * <p>{@code @Primary}를 붙이지 않는 이유: {@code MutableClock}도 {@code Clock}의 하위형이라, 여기에
   * {@code @Primary}를 붙이면 {@code testClock}과 함께 Clock 후보가 둘 다 primary가 돼 주입이 모호해진다. 테스트는 이 빈을
   * concrete 타입 {@code MutableClock}으로 주입받으므로 primary가 필요 없다.
   */
  @Bean
  public MutableClock mutableClock() {
    return new MutableClock(LocalDate.of(2026, 1, 1));
  }

  /**
   * 이름을 {@code clock}으로 두지 않는 이유: {@code ClockConfig.clock()}과 빈 이름이 겹치면 정의 오버라이드가 꺼져 있어 컨텍스트가 뜨지
   * 않는다. 이름을 다르게 두고 {@code @Primary}로 타입 주입에서 이기게 한다.
   */
  @Bean
  @Primary
  public Clock testClock(MutableClock mutableClock) {
    return mutableClock;
  }
}
