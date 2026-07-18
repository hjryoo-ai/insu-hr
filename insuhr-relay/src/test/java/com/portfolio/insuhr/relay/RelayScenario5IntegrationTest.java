package com.portfolio.insuhr.relay;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.portfolio.insuhr.domain.integration.IfSubscriber;
import com.portfolio.insuhr.relay.support.AbstractRelayIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 시나리오 5 (설계서 12장): Outbox 전송 실패 → 재시도 → 성공 시 SEND_LOG 2건, 수신측 멱등 처리.
 *
 * <p>토픽 필터를 지정해 팬아웃의 {@code JSON_EXISTS} 매칭 경로도 함께 검증한다.
 */
class RelayScenario5IntegrationTest extends AbstractRelayIntegrationTest {

  @Test
  @DisplayName("전송 실패→재시도→성공: SEND_LOG 2건(FAIL,SUCCESS), 동일 eventUuid로 재전송(수신측 멱등)")
  void failThenRetrySucceeds() {
    String path = "/webhook-" + uniq();
    IfSubscriber subscriber = createWebhookSubscriber("S5" + uniq(), path, "[\"agent.appointed\"]");
    long eventId = insertOutboxEvent("AGENT", 5001, "agent.appointed", "m1");

    // 첫 요청 500 → 상태 전이 → 이후 200 (WireMock 상태 시나리오).
    wireMock.stubFor(
        post(urlEqualTo(path))
            .inScenario("retry")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("recovered"));
    wireMock.stubFor(
        post(urlEqualTo(path))
            .inScenario("retry")
            .whenScenarioStateIs("recovered")
            .willReturn(aResponse().withStatus(200)));

    poller.runOnce(); // 팬아웃 + 1차 전송(500 실패 → 재시도 대기, 백오프 0)
    assertThat(deliveryStatus(eventId, subscriber.getId())).isEqualTo("PENDING");
    assertThat(retryCnt(eventId, subscriber.getId())).isEqualTo(1);
    assertThat(outboxStatus(eventId)).isEqualTo("FANNED_OUT");

    poller.runOnce(); // 2차 전송(200 성공)
    assertThat(deliveryStatus(eventId, subscriber.getId())).isEqualTo("SENT");
    assertThat(outboxStatus(eventId)).isEqualTo("SENT"); // 형제 전부 종결 → 요약 수렴

    // SEND_LOG 2건: FAIL(retryNo 0) → SUCCESS(retryNo 1)
    List<Map<String, Object>> logs = sendLogs(eventId);
    assertThat(logs).hasSize(2);
    assertThat(logs.get(0).get("RESULT_CD")).isEqualTo("FAIL");
    assertThat(logs.get(1).get("RESULT_CD")).isEqualTo("SUCCESS");

    // 수신측 멱등의 근거: 재전송해도 eventUuid가 같다.
    List<LoggedRequest> received = wireMock.findAll(postRequestedFor(urlEqualTo(path)));
    assertThat(received).hasSize(2);
    String uuidFirst = received.get(0).getHeader("X-InsuHR-Event-Uuid");
    String uuidSecond = received.get(1).getHeader("X-InsuHR-Event-Uuid");
    assertThat(uuidFirst).isNotBlank().isEqualTo(uuidSecond);
    // 서명·타임스탬프 헤더가 실렸다(리플레이 방어 근거).
    assertThat(received.get(0).getHeader("X-InsuHR-Signature")).isNotBlank();
    assertThat(received.get(0).getHeader("X-InsuHR-Timestamp")).isNotBlank();
  }
}
