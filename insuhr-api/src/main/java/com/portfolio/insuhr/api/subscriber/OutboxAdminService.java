package com.portfolio.insuhr.api.subscriber;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.integration.IfDeliveryDao;
import com.portfolio.insuhr.domain.integration.IfOutboxRepository;
import com.portfolio.insuhr.domain.integration.IntegrationErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 수동 재전송 (설계서 7.2 {@code POST /admin/outbox/{eventId}/resend}).
 *
 * <p>한도 초과로 FAILED된 전달 레코드를 PENDING으로 되돌린다 — 다음 폴에서 릴레이가 다시 집는다. 되돌린 건수를 돌려준다.
 */
@Service
public class OutboxAdminService {

  private final IfOutboxRepository outboxRepository;
  private final IfDeliveryDao deliveryDao;

  public OutboxAdminService(IfOutboxRepository outboxRepository, IfDeliveryDao deliveryDao) {
    this.outboxRepository = outboxRepository;
    this.deliveryDao = deliveryDao;
  }

  @Transactional
  public int resend(Long eventId) {
    outboxRepository
        .findById(eventId)
        .orElseThrow(() -> new BusinessException(IntegrationErrorCode.OUTBOX_NOT_FOUND));
    return deliveryDao.resendFailedForEvent(eventId);
  }
}
