package com.portfolio.insuhr.relay;

import com.portfolio.insuhr.domain.integration.IfDeliveryDao;
import com.portfolio.insuhr.domain.integration.IfDeliveryDao.PendingDelivery;
import com.portfolio.insuhr.domain.integration.IfDeliveryDao.ReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Outbox 릴레이 폴러 — 2단계 (설계서 9.2 v1.7/v1.8).
 *
 * <ol>
 *   <li><b>팬아웃 단계</b>: READY Outbox를 EVENT_ID 순으로 소비해 활성 구독자별 전달 레코드를 만든다(그 시점 구독자 기준 — 백필 아님).
 *   <li><b>전달 단계</b>: 전달 레코드를 (구독자, DELIVERY_ID) 순으로 보낸다. 같은 (구독자, aggId)에 미전송 선행이 있으면 후행을 보류한다(순서
 *       게이트).
 * </ol>
 *
 * <p>단일 릴레이 인스턴스를 가정한다(9.2) — 두 단계를 한 루프에서 순차 실행하므로 형제 검사·요약 갱신에 경합이 없다. 스케줄 트리거는 {@link
 * RelayScheduler}가 별도로 걸어, 테스트는 {@link #runOnce()}를 직접 호출해 결정적으로 검증한다.
 */
@Component
public class RelayPoller {

  private static final Logger log = LoggerFactory.getLogger(RelayPoller.class);

  private final IfDeliveryDao deliveryDao;
  private final RelayService relayService;
  private final WebhookPublisher publisher;
  private final RelayProperties properties;

  public RelayPoller(
      IfDeliveryDao deliveryDao,
      RelayService relayService,
      WebhookPublisher publisher,
      RelayProperties properties) {
    this.deliveryDao = deliveryDao;
    this.relayService = relayService;
    this.publisher = publisher;
    this.properties = properties;
  }

  /** 한 번의 폴 = 팬아웃 단계 → 전달 단계. */
  public void runOnce() {
    fanoutStage();
    deliveryStage();
  }

  void fanoutStage() {
    for (ReadyEvent event : deliveryDao.pickReadyEvents(properties.getBatchSize())) {
      relayService.fanoutOne(event);
    }
  }

  void deliveryStage() {
    for (PendingDelivery delivery : deliveryDao.pickPending(properties.getBatchSize())) {
      // 순서 게이트: 같은 (구독자, aggId)에 미전송(PENDING/FAILED) 선행이 있으면 이번엔 보류한다.
      if (deliveryDao.hasUnsentPredecessor(
          delivery.subscriberId(), delivery.aggType(), delivery.aggId(), delivery.deliveryId())) {
        continue;
      }
      PublishResult result = publisher.publish(delivery);
      if (result.success()) {
        relayService.recordSuccess(delivery, result);
      } else {
        log.debug(
            "웹훅 전송 실패 delivery={} system={} err={}",
            delivery.deliveryId(),
            delivery.systemCd(),
            result.error());
        relayService.recordFailure(delivery, result);
      }
    }
  }
}
