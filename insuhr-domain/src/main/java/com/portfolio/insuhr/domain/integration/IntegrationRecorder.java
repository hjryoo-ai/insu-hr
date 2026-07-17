package com.portfolio.insuhr.domain.integration;

/**
 * 기준정보 변경을 연계 계층에 기록한다 (설계서 4.3, 9.2).
 *
 * <p><b>계약</b>: 구현체는 호출한 트랜잭션 <b>안에서</b> `TB_IF_CHANGE_LOG`와 `TB_IF_OUTBOX`에 INSERT 한다. 업무 테이블 변경과
 * 이벤트 기록이 전부 성공하거나 전부 롤백돼야 유실·유령 이벤트가 없다(설계서 9.2). 그래서 이 인터페이스에는 트랜잭션 관련 어노테이션이 없다 — 호출부의 트랜잭션에 반드시
 * 합류해야 한다.
 *
 * <p>기준정보를 바꾸는 모든 애플리케이션 서비스는 `TB_IF_OUTBOX`를 직접 INSERT 하지 않고 이 인터페이스를 거친다.
 *
 * <p>Phase 6 이전에는 {@link NoOpIntegrationRecorder}가 주입된다. Phase 6에서 실제 구현으로 교체할 때 <b>서비스 코드는 건드리지
 * 않는다</b>(설계서 13.3).
 */
public interface IntegrationRecorder {

  /**
   * 기준정보 변경 1건을 기록한다.
   *
   * @param event 발행할 이벤트. 페이로드에는 마스킹된 이름과 업무키만 담는다 — 주민번호·계좌 등 민감 원문은 절대 싣지 않는다(설계서 9.3)
   */
  void record(IntegrationEvent event);
}
