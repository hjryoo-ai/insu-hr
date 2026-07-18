package com.portfolio.insuhr.relay;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.domain.integration.IfSubscriber;
import com.portfolio.insuhr.relay.support.AbstractRelayIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 순서 게이트 (설계서 9.2): 같은 (구독자, aggId)의 선행이 미전송이면 후행 보류 — 실패→보류→재시도→전송. */
class RelayOrderingGateIntegrationTest extends AbstractRelayIntegrationTest {

  @Test
  @DisplayName("동일 aggId: 선행 실패 시 후행 보류 → 선행 재시도 성공 후에야 후행 전송")
  void successorHeldUntilPredecessorSent() {
    String path = "/webhook-" + uniq();
    IfSubscriber subscriber = createWebhookSubscriber("ORD" + uniq(), path, null);
    long aggId = 7000 + (System.nanoTime() % 1000);

    long eventN = insertOutboxEvent("AGENT", aggId, "agent.appointed", "N"); // 선행(낮은 EVENT_ID)
    long eventN1 =
        insertOutboxEvent("AGENT", aggId, "agent.status.changed", "N1"); // 후행(높은 EVENT_ID)

    // 첫 요청(선행 N)은 500, 이후는 200.
    wireMock.stubFor(
        post(urlEqualTo(path))
            .inScenario("ord")
            .whenScenarioStateIs(STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("up"));
    wireMock.stubFor(
        post(urlEqualTo(path))
            .inScenario("ord")
            .whenScenarioStateIs("up")
            .willReturn(aResponse().withStatus(200)));

    poller.runOnce(); // N 전송 실패, N1은 게이트로 보류
    assertThat(deliveryStatus(eventN, subscriber.getId())).isEqualTo("PENDING");
    assertThat(deliveryStatus(eventN1, subscriber.getId())).isEqualTo("PENDING");
    // 후행 N1은 아직 한 번도 전송 시도되지 않았다.
    wireMock.verify(
        0, postRequestedFor(urlEqualTo(path)).withRequestBody(containing("\"marker\":\"N1\"")));
    assertThat(retryCnt(eventN1, subscriber.getId())).isZero();

    poller.runOnce(); // N 재시도 성공 → 게이트 풀림 → N1 전송 성공
    assertThat(deliveryStatus(eventN, subscriber.getId())).isEqualTo("SENT");
    assertThat(deliveryStatus(eventN1, subscriber.getId())).isEqualTo("SENT");
    // 후행 N1은 선행 N이 SENT된 뒤에 정확히 한 번 전송됐다.
    wireMock.verify(
        1, postRequestedFor(urlEqualTo(path)).withRequestBody(containing("\"marker\":\"N1\"")));
  }
}
