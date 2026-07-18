package com.portfolio.insuhr.relay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 릴레이 폴 스케줄 트리거 (설계서 9.2).
 *
 * <p>스케줄링을 폴러 로직에서 분리한 이유: 통합 테스트는 {@code insuhr.relay.scheduler.enabled=false}로 이 빈을 끄고 {@link
 * RelayPoller#runOnce()}를 직접 호출해 <b>결정적으로</b> 검증한다 — 타이머와 수동 호출이 겹쳐 이중 처리되는 것을 막는다.
 */
@Component
@ConditionalOnProperty(
    name = "insuhr.relay.scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RelayScheduler {

  private final RelayPoller poller;

  public RelayScheduler(RelayPoller poller) {
    this.poller = poller;
  }

  @Scheduled(fixedDelayString = "${insuhr.relay.poll-interval-ms:2000}")
  public void tick() {
    poller.runOnce();
  }
}
