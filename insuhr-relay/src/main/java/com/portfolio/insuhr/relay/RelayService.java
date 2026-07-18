package com.portfolio.insuhr.relay;

import com.portfolio.insuhr.domain.integration.IfDeliveryDao;
import com.portfolio.insuhr.domain.integration.IfDeliveryDao.PendingDelivery;
import com.portfolio.insuhr.domain.integration.IfDeliveryDao.ReadyEvent;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 릴레이 트랜잭션 단위 (설계서 9.2 v1.8).
 *
 * <p>웹훅 전송(I/O)은 트랜잭션 밖에서 하고, <b>결과 반영만</b> 여기서 짧은 트랜잭션으로 원자 처리한다 — 전송 중 DB 커넥션을 붙잡지 않는다. 팬아웃도 이벤트
 * 1건 단위로 원자 처리(전달 레코드 생성 + Outbox 소비 표시가 함께 커밋).
 */
@Service
public class RelayService {

  private final IfDeliveryDao deliveryDao;
  private final PolicyConfigService policyConfig;
  private final RelayProperties properties;

  public RelayService(
      IfDeliveryDao deliveryDao, PolicyConfigService policyConfig, RelayProperties properties) {
    this.deliveryDao = deliveryDao;
    this.policyConfig = policyConfig;
    this.properties = properties;
  }

  /** 팬아웃: 활성 구독자에게 전달 레코드 생성 후 Outbox 소비 표시(대상 없으면 즉시 SENT). */
  @Transactional
  public void fanoutOne(ReadyEvent event) {
    int inserted =
        deliveryDao.fanout(event.eventId(), event.aggType(), event.aggId(), event.eventType());
    if (inserted == 0) {
      deliveryDao.markOutboxSentNoTarget(event.eventId());
    } else {
      deliveryDao.markOutboxFannedOut(event.eventId());
    }
  }

  /** 전송 성공 반영: 전달 SENT + 전송이력 + 형제 전부 종결이면 Outbox 요약 수렴. */
  @Transactional
  public void recordSuccess(PendingDelivery delivery, PublishResult result) {
    deliveryDao.markDeliverySent(delivery.deliveryId());
    deliveryDao.insertSendLog(
        delivery.eventId(),
        delivery.systemCd(),
        true,
        result.httpStatus(),
        result.body(),
        delivery.retryCnt());
    if (deliveryDao.allSiblingsTerminal(delivery.eventId())) {
      deliveryDao.markOutboxSentSummary(delivery.eventId());
    }
  }

  /** 전송 실패 반영: 전송이력 + (한도 내면) 백오프 재시도, (초과면) FAILED. */
  @Transactional
  public void recordFailure(PendingDelivery delivery, PublishResult result) {
    int attempt = delivery.retryCnt();
    deliveryDao.insertSendLog(
        delivery.eventId(),
        delivery.systemCd(),
        false,
        result.httpStatus(),
        result.error(),
        attempt);

    int maxRetry = policyConfig.getInt(PolicyKey.OUTBOX_MAX_RETRY);
    if (attempt + 1 >= maxRetry) {
      deliveryDao.markDeliveryFailed(delivery.deliveryId(), result.error());
    } else {
      deliveryDao.markDeliveryRetry(
          delivery.deliveryId(), backoffSeconds(attempt + 1), result.error());
    }
  }

  private long backoffSeconds(int retryNo) {
    // base * 2^(retryNo-1). shift 상한으로 오버플로/폭주 방지. base=0(테스트)이면 즉시 재시도.
    int shift = Math.min(retryNo - 1, 16);
    return properties.getRetryBaseSeconds() << shift;
  }
}
