package com.portfolio.insuhr.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 설계사 상태머신 테스트용 빈 주입.
 *
 * <p>실제 {@code NoOpIntegrationRecorder}·{@code AlwaysSatisfiedRequirementChecker} 대신 검증 가능한 대역을
 * {@code @Primary}로 올린다. 이 설정을 {@code @Import}한 테스트만 별도 컨텍스트를 갖는다.
 *
 * <p><b>concrete 타입 하나에 {@code @Primary}만 붙인다.</b> {@code RecordingIntegrationRecorder}가 {@code
 * IntegrationRecorder}이기도 하므로, 이 하나가 concrete 주입과 인터페이스 primary를 겸한다. 인터페이스용 빈을 따로 또
 * {@code @Primary}로 두면 primary 후보가 둘이 돼 {@code NoUniqueBeanDefinitionException}이 난다(Phase 3 clock에서
 * 밟은 함정).
 */
@TestConfiguration
public class AgentTestConfig {

  @Bean
  @Primary
  public RecordingIntegrationRecorder recordingIntegrationRecorder() {
    return new RecordingIntegrationRecorder();
  }

  @Bean
  @Primary
  public ConfigurableRequirementChecker configurableRequirementChecker() {
    return new ConfigurableRequirementChecker();
  }
}
