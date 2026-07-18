package com.portfolio.insuhr.domain.integration;

/**
 * 아웃박스 이벤트 요약 상태 (설계서 9.2).
 *
 * <p>다중 구독자 팬아웃(v1.7) 이후 실제 전송 상태는 {@code TB_IF_DELIVERY}(이벤트×구독자)가 갖고, 이 값은 그 요약이다. 릴레이는 {@code
 * READY → FANNED_OUT → SENT}로 전이시킨다 — 팬아웃이 READY만 집어 전달 레코드를 만들고 FANNED_OUT으로 표시(소비 표시), 전 전달 레코드가
 * 종결(SENT/SKIPPED)되면 SENT. 매칭 구독자 0명이면 팬아웃에서 곧바로 SENT(대상 없음).
 */
public enum OutboxStatus {
  /** 전송 대기 — 팬아웃 미처리 */
  READY,
  /** 팬아웃 완료 — 전달 레코드 생성됨, 전달 진행 중 */
  FANNED_OUT,
  /** 요약 완료 — 전 전달 레코드가 SENT 또는 SKIPPED */
  SENT,
  /** 재시도 한도 초과 — 수동 재전송 대상 */
  FAILED,
  /** 구독자 없음 등으로 건너뜀 */
  SKIPPED
}
