package com.portfolio.insuhr.domain.integration;

/** 아웃박스 이벤트 전송 상태 (설계서 9.2). */
public enum OutboxStatus {
  /** 전송 대기 */
  READY,
  /** 전송 완료 */
  SENT,
  /** 재시도 한도 초과 — 수동 재전송 대상 */
  FAILED,
  /** 구독자 없음 등으로 건너뜀 */
  SKIPPED
}
