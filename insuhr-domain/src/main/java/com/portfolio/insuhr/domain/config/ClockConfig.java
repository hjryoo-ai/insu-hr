package com.portfolio.insuhr.domain.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각 공급자 (설계서 13.2 Phase 3 v1.4).
 *
 * <p><b>왜 빈으로 두는가</b>: 도메인이 {@code LocalDate.now()}를 직접 부르면 날짜 경계를 테스트할 수 없다. "발령일이 오늘인 건은 반영, 내일
 * 건은 미반영"을 검증하려면 시스템 날짜를 바꾸는 수밖에 없기 때문이다. 시각을 주입받으면 테스트가 {@code Clock.fixed(...)}로 원하는 날짜에 서 있을 수
 * 있다.
 *
 * <p>Phase 3의 발령 반영 규칙, Phase 5의 모집자격 판정(경계일자), Phase 7의 배치가 모두 이 빈 위에서 돈다.
 *
 * <p><b>존이 UTC가 아니라 업무존(KST)인 이유 — 설계서 6.2와 충돌하지 않는다</b>
 *
 * <p>6.2의 "모든 시각은 UTC로 적재"는 <b>타임스탬프</b> 규약이고, 이 존은 <b>업무 날짜</b>를 정한다. 둘은 다른 문제다:
 *
 * <ul>
 *   <li>{@code Instant.now(clock)}는 존과 무관하게 같은 절대시각이다 — UTC 적재 규약은 이 존의 영향을 받지 않는다.
 *   <li>{@code LocalDate.now(clock)}는 존을 따른다. 여기서 UTC를 쓰면 <b>한국시간 00:00~09:00 사이의 "오늘"이 전날로
 *       계산된다.</b> 새벽 00:10에 도는 {@code futureAppointApplyJob}(설계서 8장)이 정확히 그 구간에서 돌므로, UTC 존이면 발령일
 *       도래분을 하루 늦게 반영한다.
 * </ul>
 *
 * <p>발령일·입사일·휴가일 같은 {@code DATE} 컬럼은 한국 영업일 기준의 업무 사실이지 절대시각이 아니다. 그래서 업무 날짜를 뽑는 존은 KST로 고정한다.
 */
@Configuration
public class ClockConfig {

  /** 업무 날짜 기준 존. 발령일·입사일 등 DATE 컬럼이 의미하는 "그 날"의 기준이다. */
  public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

  @Bean
  public Clock clock() {
    return Clock.system(BUSINESS_ZONE);
  }
}
