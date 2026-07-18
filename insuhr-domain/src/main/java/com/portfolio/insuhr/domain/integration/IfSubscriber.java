package com.portfolio.insuhr.domain.integration;

import com.portfolio.insuhr.domain.support.BaseEntity;
import com.portfolio.insuhr.domain.support.YnConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 구독(연계 대상) 시스템 (설계서 6.5, 9.2).
 *
 * <p>릴레이 팬아웃이 <b>활성(useYn) + WEBHOOK + TOPIC_FILTER 매칭</b> 구독자에게 전달 레코드를 만든다. 비활성화하면 미전송 전달 레코드가
 * SKIPPED로 종결돼 Outbox 요약이 수렴한다(9.2 v1.7). 시크릿은 AES 암호문으로 저장하고 전송 시 복호화해 HMAC 키로 쓴다.
 */
@Entity
@Table(name = "TB_IF_SUBSCRIBER")
public class IfSubscriber extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "SUBSCRIBER_ID")
  private Long id;

  @Column(name = "SYSTEM_CD", nullable = false, length = 30)
  private String systemCd;

  @Column(name = "SYSTEM_NM", nullable = false, length = 100)
  private String systemNm;

  @Column(name = "DELIVERY_TYPE_CD", nullable = false, length = 10)
  private String deliveryTypeCd;

  @Column(name = "ENDPOINT_URL", length = 500)
  private String endpointUrl;

  @Column(name = "SECRET_ENC", length = 256)
  private String secretEnc;

  @Lob
  @Column(name = "TOPIC_FILTER")
  private String topicFilter;

  @Convert(converter = YnConverter.class)
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "USE_YN", nullable = false, length = 1)
  private boolean useYn;

  protected IfSubscriber() {}

  private IfSubscriber(
      String systemCd,
      String systemNm,
      String deliveryTypeCd,
      String endpointUrl,
      String secretEnc,
      String topicFilter) {
    this.systemCd = systemCd;
    this.systemNm = systemNm;
    this.deliveryTypeCd = deliveryTypeCd;
    this.endpointUrl = endpointUrl;
    this.secretEnc = secretEnc;
    this.topicFilter = topicFilter;
    this.useYn = true;
  }

  /**
   * @param secretEnc 서명 시크릿의 AES 암호문(평문 금지 — 서비스가 암호화해 넘긴다)
   * @param topicFilter 관심 eventType 목록 JSON 배열(null=전체)
   */
  public static IfSubscriber create(
      String systemCd,
      String systemNm,
      String deliveryTypeCd,
      String endpointUrl,
      String secretEnc,
      String topicFilter) {
    return new IfSubscriber(
        systemCd, systemNm, deliveryTypeCd, endpointUrl, secretEnc, topicFilter);
  }

  public void deactivate() {
    this.useYn = false;
  }

  public void activate() {
    this.useYn = true;
  }

  public void updateEndpoint(String endpointUrl) {
    this.endpointUrl = endpointUrl;
  }

  public void updateSecret(String secretEnc) {
    this.secretEnc = secretEnc;
  }

  public Long getId() {
    return id;
  }

  public String getSystemCd() {
    return systemCd;
  }

  public String getSystemNm() {
    return systemNm;
  }

  public String getDeliveryTypeCd() {
    return deliveryTypeCd;
  }

  public String getEndpointUrl() {
    return endpointUrl;
  }

  public String getTopicFilter() {
    return topicFilter;
  }

  public boolean isActive() {
    return useYn;
  }
}
