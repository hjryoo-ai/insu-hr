package com.portfolio.insuhr.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 위촉 요건검증을 테스트가 좌우하도록 실판정 대신 {@link ConfigurableRequirementChecker}를 주입한다.
 *
 * <p>상태머신 자체를 검증하는 테스트(전이·재위촉·동시성 등)는 실제 자격 데이터를 세팅하지 않고 이 대역으로 appoint를 통과시킨다. 자격 판정을 실제로 검증하는
 * 테스트는 이 설정을 <b>import하지 않아</b> 실판정 {@code EligibilityRequirementChecker}가 쓰이게 한다(시나리오 1b).
 */
@TestConfiguration
public class StubRequirementCheckerConfig {

  @Bean
  @Primary
  public ConfigurableRequirementChecker configurableRequirementChecker() {
    return new ConfigurableRequirementChecker();
  }
}
