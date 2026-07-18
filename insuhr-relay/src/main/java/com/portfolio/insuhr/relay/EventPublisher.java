package com.portfolio.insuhr.relay;

import com.portfolio.insuhr.domain.integration.IfDeliveryDao.PendingDelivery;

/**
 * 이벤트 전송 포트 (설계서 9.1, 8 v2.2 Phase 8).
 *
 * <p>전달 단계는 이 포트로만 전송한다 — {@link RelayPoller}는 전달 레코드의 {@code DELIVERY_TYPE_CD}로 담당 구현을 고른다. 웹훅은
 * 기본으로 항상 있고({@link WebhookPublisher}), Kafka는 {@code kafka} 프로파일에서만 등록된다. 팬아웃·순서 게이트는 전송 타입과 무관하게
 * 같으므로, KAFKA 구독자도 같은 게이트를 타고 전송만 이 포트의 다른 구현으로 갈린다.
 */
public interface EventPublisher {

  /** 담당 전송 타입 ({@code TB_IF_SUBSCRIBER.DELIVERY_TYPE_CD}와 매칭 — WEBHOOK/KAFKA). */
  String deliveryType();

  /** 전달 레코드 1건을 전송하고 결과를 돌려준다(성공/실패 판정은 호출자 {@link RelayService}가 반영). */
  PublishResult publish(PendingDelivery delivery);
}
