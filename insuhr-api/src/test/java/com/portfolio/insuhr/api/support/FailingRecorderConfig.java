package com.portfolio.insuhr.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 실패하는 recorder를 {@code @Primary}로 올려 실구현 {@code OutboxIntegrationRecorder}를 가린다.
 *
 * <p>concrete 하나에 {@code @Primary}만 붙인다 — {@link FailingIntegrationRecorder}가 {@code
 * IntegrationRecorder}이기도 하므로 인터페이스 primary를 겸한다(AgentTestConfig와 같은 이유).
 */
@TestConfiguration
public class FailingRecorderConfig {

  @Bean
  @Primary
  public FailingIntegrationRecorder failingIntegrationRecorder() {
    return new FailingIntegrationRecorder();
  }
}
