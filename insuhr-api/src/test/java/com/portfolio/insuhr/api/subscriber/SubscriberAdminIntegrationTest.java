package com.portfolio.insuhr.api.subscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.common.crypto.AesGcmCipher;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.integration.IfSubscriber;
import com.portfolio.insuhr.domain.integration.IfSubscriberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 구독 시스템 관리 (설계서 7.2 /admin/subscribers).
 *
 * <p>시크릿은 평문으로 저장되지 않고 AES 암호문(키버전 프리픽스)으로만 남으며, 중복 시스템코드는 409로 막힌다.
 */
class SubscriberAdminIntegrationTest extends AbstractIntegrationTest {

  @Autowired IfSubscriberService subscriberService;
  @Autowired AesGcmCipher cipher;
  @Autowired JdbcClient jdbcClient;

  private String uniq() {
    return Long.toString(System.nanoTime() % 100_000_000L);
  }

  @Test
  @DisplayName("구독자 생성 시 시크릿은 암호문으로만 저장되고, 중복 시스템코드는 409로 막힌다")
  void createEncryptsSecretAndBlocksDuplicate() {
    String cd = "SUB" + uniq();
    IfSubscriber created =
        subscriberService.create(
            cd,
            cd + " 시스템",
            "WEBHOOK",
            "https://example/hook",
            "my-secret",
            "[\"agent.appointed\"]");

    assertThat(created.getId()).isNotNull();
    assertThat(created.isActive()).isTrue();

    // 시크릿은 평문이 아니라 키버전 프리픽스 붙은 암호문으로 저장된다.
    String stored =
        jdbcClient
            .sql("SELECT SECRET_ENC FROM TB_IF_SUBSCRIBER WHERE SUBSCRIBER_ID = :id")
            .param("id", created.getId())
            .query(String.class)
            .single();
    assertThat(stored).startsWith("v1:").doesNotContain("my-secret");
    assertThat(cipher.decrypt(stored)).isEqualTo("my-secret");

    // 같은 시스템코드 재등록은 409.
    assertThatThrownBy(() -> subscriberService.create(cd, "중복", "WEBHOOK", "https://x", "s", null))
        .isInstanceOf(BusinessException.class);
  }
}
