package com.portfolio.insuhr.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 발행 이벤트를 캡처하는 recorder 주입 (전이·판정 이벤트 검증용).
 *
 * <p>실제 {@code NoOpIntegrationRecorder} 대신 {@link RecordingIntegrationRecorder}를 {@code @Primary}로
 * 올린다. 위촉 요건검증 대역이 필요한 상태머신 테스트는 {@link StubRequirementCheckerConfig}를 함께 {@code @Import}하고, 자격 판정을
 * 실제로 도는 테스트(시나리오 1b 등)는 그걸 import하지 않아 실판정 {@code EligibilityRequirementChecker}를 그대로 쓴다.
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
}
