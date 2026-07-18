package com.portfolio.insuhr.relay;

import com.portfolio.insuhr.domain.integration.IfDeliveryDao;
import com.portfolio.insuhr.domain.integration.IfDeliveryDao.PendingDelivery;
import com.portfolio.insuhr.domain.integration.IfDeliveryDao.ReadyEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
  private final Map<String, EventPublisher> publishers;
  private final RelayProperties properties;

  public RelayPoller(
      IfDeliveryDao deliveryDao,
      RelayService relayService,
      List<EventPublisher> publishers,
      RelayProperties properties) {
    this.deliveryDao = deliveryDao;
    this.relayService = relayService;
    // 전송 타입 → 퍼블리셔. 웹훅은 항상, Kafka는 프로파일에서만 등록된다(설계서 8 v2.2).
    this.publishers =
        publishers.stream()
            .collect(Collectors.toMap(EventPublisher::deliveryType, Function.identity()));
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
      PublishResult result = publish(delivery);
      if (result.success()) {
        relayService.recordSuccess(delivery, result);
      } else {
        log.debug(
            "전송 실패 delivery={} type={} system={} err={}",
            delivery.deliveryId(),
            delivery.deliveryTypeCd(),
            delivery.systemCd(),
            result.error());
        relayService.recordFailure(delivery, result);
      }
    }
  }

  /**
   * 전달 레코드의 전송 타입에 맞는 퍼블리셔로 보낸다.
   *
   * <p><b>담당 퍼블리셔가 없으면</b>(예: KAFKA 구독자인데 {@code kafka} 프로파일이 꺼짐) 실패로 반영한다(설계서 8 v2.2) — 조용히
   * PENDING에 두면 {@code outboxDlqSweepJob}이 정체로 오탐하고, SKIPPED로 종결하면 프로파일을 켜도 재전송이 안 된다. 실패로 두면 한도 내엔
   * 재시도, 초과하면 FAILED가 되어 프로파일 활성화 후 {@code POST /admin/outbox/{id}/resend}로 회복 가능하다.
   */
  private PublishResult publish(PendingDelivery delivery) {
    EventPublisher publisher = publishers.get(delivery.deliveryTypeCd());
    if (publisher == null) {
      log.warn(
          "전송 타입 미지원 delivery={} type={} (해당 프로파일 미활성?)",
          delivery.deliveryId(),
          delivery.deliveryTypeCd());
      return PublishResult.failure(null, "지원하지 않는 전송 타입: " + delivery.deliveryTypeCd());
    }
    return publisher.publish(delivery);
  }
}
