package com.portfolio.insuhr.domain.integration;

import com.portfolio.insuhr.common.crypto.AesGcmCipher;
import com.portfolio.insuhr.common.exception.BusinessException;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구독 시스템 관리 (설계서 7.2 {@code /admin/subscribers}, 9.2).
 *
 * <p>규칙이 domain에 사는 이유: <b>비활성화 시 미전송 전달 레코드를 SKIPPED로 종결하고 Outbox 요약을 재수렴</b>시키는 것이 순수 CRUD가 아닌 연계
 * 도메인 규칙이라서다(9.2 v1.7). api 컨트롤러와 릴레이 테스트가 같은 규칙을 공유한다.
 *
 * <p>시크릿은 평문을 받아 AES로 암호화해 저장한다 — 평문은 DB에 남지 않는다(10.3).
 */
@Service
public class IfSubscriberService {

  private final IfSubscriberRepository subscriberRepository;
  private final IfDeliveryDao deliveryDao;
  private final AesGcmCipher cipher;

  public IfSubscriberService(
      IfSubscriberRepository subscriberRepository, IfDeliveryDao deliveryDao, AesGcmCipher cipher) {
    this.subscriberRepository = subscriberRepository;
    this.deliveryDao = deliveryDao;
    this.cipher = cipher;
  }

  @Transactional
  public IfSubscriber create(
      String systemCd,
      String systemNm,
      String deliveryTypeCd,
      String endpointUrl,
      String plainSecret,
      String topicFilterJson) {
    subscriberRepository
        .findBySystemCd(systemCd)
        .ifPresent(
            existing -> {
              throw new BusinessException(IntegrationErrorCode.DUPLICATE_SUBSCRIBER);
            });
    String secretEnc = cipher.encrypt(plainSecret);
    return subscriberRepository.save(
        IfSubscriber.create(
            systemCd, systemNm, deliveryTypeCd, endpointUrl, secretEnc, topicFilterJson));
  }

  /**
   * 구독자를 비활성화한다. 미전송 전달 레코드를 SKIPPED로 종결하고, 그로 인해 전 형제가 종결된 이벤트의 Outbox 요약을 SENT로 수렴시킨다.
   *
   * <p>이 수렴이 없으면 죽은 구독자의 미전송분 때문에 Outbox 요약이 영원히 FANNED_OUT에 머문다(9.2 v1.7).
   */
  @Transactional
  public void deactivate(Long subscriberId) {
    IfSubscriber subscriber = getOrThrow(subscriberId);
    subscriber.deactivate();
    List<Long> affectedEvents = deliveryDao.skipUnsentForSubscriber(subscriberId);
    for (Long eventId : new LinkedHashSet<>(affectedEvents)) {
      if (deliveryDao.allSiblingsTerminal(eventId)) {
        deliveryDao.markOutboxSentSummary(eventId);
      }
    }
  }

  @Transactional
  public void activate(Long subscriberId) {
    getOrThrow(subscriberId).activate();
  }

  @Transactional(readOnly = true)
  public List<IfSubscriber> list() {
    return subscriberRepository.findAllByOrderBySystemCdAsc();
  }

  @Transactional(readOnly = true)
  public IfSubscriber get(Long subscriberId) {
    return getOrThrow(subscriberId);
  }

  private IfSubscriber getOrThrow(Long subscriberId) {
    return subscriberRepository
        .findById(subscriberId)
        .orElseThrow(() -> new BusinessException(IntegrationErrorCode.SUBSCRIBER_NOT_FOUND));
  }
}
