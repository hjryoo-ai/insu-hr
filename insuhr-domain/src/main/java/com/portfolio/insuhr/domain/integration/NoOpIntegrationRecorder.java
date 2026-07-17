package com.portfolio.insuhr.domain.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase 6 이전의 no-op 구현 (설계서 13.3).
 *
 * <p>이벤트를 발행하지 않고 로그만 남긴다. 서비스가 Phase 2부터 {@link IntegrationRecorder}를 호출하도록 해두면, Phase 6에서 실제 구현을
 * 붙이는 것만으로 발행이 켜진다 — <b>서비스 코드는 건드리지 않는다</b>.
 *
 * <p><b>Phase 6 작업</b>: 실제 구현(Outbox+ChangeLog INSERT)을 추가하고 <b>이 클래스를 삭제</b>한다. 조건부 등록
 * ({@code @ConditionalOnMissingBean})으로 자동 교체되게 하지 않는 이유는, 그 어노테이션이 자동설정의 {@code @Bean} 메서드용이라 일반
 * {@code @Component}에서는 평가 순서가 보장되지 않기 때문이다. 스텁이 조용히 살아남아 이벤트가 발행되지 않는 것보다, 빈 충돌로 기동이 실패해 즉시 알아차리는
 * 편이 낫다.
 */
@Component
public class NoOpIntegrationRecorder implements IntegrationRecorder {

  private static final Logger log = LoggerFactory.getLogger(NoOpIntegrationRecorder.class);

  @Override
  public void record(IntegrationEvent event) {
    log.debug(
        "[no-op] 연계 이벤트 (Phase 6에서 실제 발행으로 교체): type={} agg={}:{} businessKey={}",
        event.eventType(),
        event.aggregateType(),
        event.aggregateId(),
        event.businessKey());
  }
}
