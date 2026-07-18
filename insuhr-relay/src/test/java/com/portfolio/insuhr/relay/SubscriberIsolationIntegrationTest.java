package com.portfolio.insuhr.relay;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.domain.integration.IfSubscriber;
import com.portfolio.insuhr.relay.support.AbstractRelayIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 구독자 격리 + 비활성 종결 수렴 (설계서 9.2 v1.7/v1.8, 13.2 완료 기준).
 *
 * <p>① 죽은 구독자 하나가 다른 구독자의 전달을 막지 않는다. ② 비활성화하면 미전송분이 SKIPPED로 종결돼 Outbox 요약이 SENT로 수렴한다 — 안 그러면 죽은
 * 구독자 때문에 요약이 영원히 FANNED_OUT에 머문다.
 */
class SubscriberIsolationIntegrationTest extends AbstractRelayIntegrationTest {

  @Test
  @DisplayName("죽은 구독자가 다른 구독자를 막지 않고, 비활성화 시 미전송분 SKIPPED로 요약이 수렴한다")
  void deadSubscriberIsolatedThenDeactivationConverges() {
    String pathA = "/webhook-a-" + uniq();
    String pathB = "/webhook-b-" + uniq();
    IfSubscriber subA = createWebhookSubscriber("A" + uniq(), pathA, null);
    IfSubscriber subB = createWebhookSubscriber("B" + uniq(), pathB, null);
    long eventId = insertOutboxEvent("AGENT", 9001, "agent.appointed", "iso");

    wireMock.stubFor(post(urlEqualTo(pathA)).willReturn(aResponse().withStatus(200)));
    wireMock.stubFor(post(urlEqualTo(pathB)).willReturn(aResponse().withStatus(500))); // B는 죽음

    poller.runOnce();

    // 격리: A는 성공, B는 실패해도 A는 영향 없음.
    assertThat(deliveryStatus(eventId, subA.getId())).isEqualTo("SENT");
    assertThat(deliveryStatus(eventId, subB.getId())).isEqualTo("PENDING");
    assertThat(outboxStatus(eventId)).isEqualTo("FANNED_OUT"); // B 때문에 아직 수렴 못함
    wireMock.verify(1, postRequestedFor(urlEqualTo(pathA)));

    // 비활성 종결: B 비활성화 → 미전송분 SKIPPED → 전 형제 종결 → 요약 SENT로 수렴.
    subscriberService.deactivate(subB.getId());
    assertThat(deliveryStatus(eventId, subB.getId())).isEqualTo("SKIPPED");
    assertThat(outboxStatus(eventId)).isEqualTo("SENT");
  }
}
