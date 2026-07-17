package com.portfolio.insuhr.api.support;

import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.integration.IntegrationRecorder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 발행된 연계 이벤트를 캡처하는 테스트용 recorder.
 *
 * <p>Phase 6의 실제 발행 대신, 전이마다 recorder가 <b>정확히 1회, 올바른 eventType으로</b> 호출됐는지를 테스트가 확인할 수 있게 한다(설계서
 * 5.3 v1.5 — 배선 누락 방지). 상태머신이 recorder를 구조적으로 호출하는지 검증하는 것이 목적이다.
 *
 * <p>동시 전이 테스트에서 여러 스레드가 부르므로 {@link CopyOnWriteArrayList}로 담는다.
 */
public class RecordingIntegrationRecorder implements IntegrationRecorder {

  private final List<IntegrationEvent> events = new CopyOnWriteArrayList<>();

  @Override
  public void record(IntegrationEvent event) {
    events.add(event);
  }

  public List<IntegrationEvent> events() {
    return events;
  }

  public void clear() {
    events.clear();
  }
}
