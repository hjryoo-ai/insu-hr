package com.portfolio.insuhr.relay;

import com.portfolio.insuhr.common.crypto.AesGcmCipher;
import com.portfolio.insuhr.common.crypto.HmacSha256;
import com.portfolio.insuhr.domain.integration.IfDeliveryDao.PendingDelivery;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 웹훅 전송기 (설계서 9.2).
 *
 * <p><b>서명은 전송 바이트 그대로</b>: 저장된 payload를 재직렬화하지 않고 그 문자열에 서명·전송한다(재직렬화는 바이트가 달라질 수 있다). 서명 입력 =
 * {@code timestamp + "." + body}, 헤더 {@code X-InsuHR-Timestamp} — 본문만 덮으면 캡처된 요청을 무한 재생할 수 있으므로
 * 타임스탬프까지 서명에 넣어 리플레이를 막는다. 시크릿은 {@code SECRET_ENC}를 AES 복호화해 HMAC 키로 쓴다.
 *
 * <p>서명 타임스탬프는 앱 시계({@code Instant.now(clock)})로 찍는다 — 절대시각이라 존 무관(6.2). 수신측이 스큐 창 밖이면 거절한다.
 */
@Component
public class WebhookPublisher implements EventPublisher {

  static final String HEADER_SIGNATURE = "X-InsuHR-Signature";
  static final String HEADER_TIMESTAMP = "X-InsuHR-Timestamp";
  static final String HEADER_EVENT_UUID = "X-InsuHR-Event-Uuid";

  private final RestClient restClient;
  private final AesGcmCipher cipher;
  private final Clock clock;

  public WebhookPublisher(RestClient relayRestClient, AesGcmCipher cipher, Clock clock) {
    this.restClient = relayRestClient;
    this.cipher = cipher;
    this.clock = clock;
  }

  @Override
  public String deliveryType() {
    return "WEBHOOK";
  }

  @Override
  public PublishResult publish(PendingDelivery delivery) {
    String timestamp = Instant.now(clock).toString();
    String body = delivery.payload();

    String signature;
    try {
      String secret = cipher.decrypt(delivery.secretEnc());
      signature = HmacSha256.hex(secret, timestamp + "." + body);
    } catch (RuntimeException e) {
      return PublishResult.failure(null, "서명 준비 실패: " + e.getMessage());
    }

    try {
      ResponseEntity<String> response =
          restClient
              .post()
              .uri(URI.create(delivery.endpointUrl()))
              .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
              .header(HEADER_TIMESTAMP, timestamp)
              .header(HEADER_SIGNATURE, signature)
              .header(HEADER_EVENT_UUID, delivery.eventUuid())
              .body(body)
              .retrieve()
              // 4xx/5xx도 예외로 던지지 않고 상태코드로 성공을 판정한다(재시도 큐가 다룬다).
              .onStatus(HttpStatusCode::isError, (request, errorResponse) -> {})
              .toEntity(String.class);

      int status = response.getStatusCode().value();
      if (response.getStatusCode().is2xxSuccessful()) {
        return PublishResult.success(status, response.getBody());
      }
      return PublishResult.failure(status, "HTTP " + status);
    } catch (RestClientException e) {
      // 연결 거부·타임아웃 등 — 상태 없음.
      return PublishResult.failure(null, e.getMessage());
    }
  }
}
