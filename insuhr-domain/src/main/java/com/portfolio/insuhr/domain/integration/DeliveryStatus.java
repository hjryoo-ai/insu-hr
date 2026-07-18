package com.portfolio.insuhr.domain.integration;

/**
 * 전달 레코드(이벤트×구독자) 상태 (설계서 9.2 v1.7/v1.8).
 *
 * <p>순서 게이트의 <b>종결 집합 = {SENT, SKIPPED}</b> — 선행이 {PENDING, FAILED}면 후행 보류, {SENT, SKIPPED}면 통과.
 * FAILED(재시도 소진)도 순서상 후행을 막는다. SKIPPED(구독자 비활성)는 후행을 막지 않는다.
 */
public enum DeliveryStatus {
  /** 전송 대기(신규·재시도 대기 포함). {@code NEXT_RETRY_AT <= 현재}면 픽업 대상 */
  PENDING,
  /** 전송 완료 — 종결 */
  SENT,
  /** 재시도 한도 초과 — 수동 재전송 대상(후행 보류 유지) */
  FAILED,
  /** 구독자 비활성화로 종결 — 후행을 막지 않음 */
  SKIPPED
}
