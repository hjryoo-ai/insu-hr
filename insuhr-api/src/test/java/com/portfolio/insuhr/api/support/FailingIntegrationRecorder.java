package com.portfolio.insuhr.api.support;

import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.integration.IntegrationRecorder;

/**
 * 항상 실패하는 recorder — 트랜잭셔널 아웃박스의 존재 이유를 검증하는 대역 (설계서 9.2 v1.6).
 *
 * <p>recorder가 호출자 트랜잭션에서 쓰므로, 기록이 실패하면 업무 변경도 함께 롤백돼야 한다(기록 없으면 변경 없음). 이 대역을 주입하면 업무 행이 커밋되지 않음을
 * 단언할 수 있다.
 */
public class FailingIntegrationRecorder implements IntegrationRecorder {

  @Override
  public void record(IntegrationEvent event) {
    throw new IllegalStateException("주입된 recorder 실패 (테스트) — 업무 트랜잭션이 롤백돼야 한다");
  }
}
