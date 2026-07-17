package com.portfolio.insuhr.domain.config;

import com.portfolio.insuhr.domain.audit.CurrentActorProvider;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** BaseEntity의 CREATED_BY/UPDATED_BY를 채우는 감사 설정 (설계서 6.2). */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

  /**
   * 현재 행위자를 돌려준다.
   *
   * <p>api 모듈은 SecurityContext에서 로그인 ID를 읽는 {@link CurrentActorProvider}를 등록한다. <b>배치·릴레이는 인증 컨텍스트가
   * 없어 등록하지 않으며</b>, 그 경우 {@code SYSTEM-{모듈명}}으로 폴백한다(설계서 13.2 v1.2). 폴백이 없으면 배치가 감사컬럼 NOT NULL 제약에
   * 걸려 넘어진다.
   *
   * <p>{@code ObjectProvider}로 받는 이유: 구현이 있을 수도 없을 수도 있고, 어느 쪽이든 빈 정의 순서에 영향받지 않아야 한다.
   *
   * <p>api 모듈이라도 인증 없는 경로(스케줄러, 시드, 테스트 픽스처)에서는 provider가 empty를 주므로 같은 폴백을 탄다.
   */
  @Bean
  public AuditorAware<String> auditorAware(
      ObjectProvider<CurrentActorProvider> actorProviders,
      @Value("${spring.application.name:insuhr}") String applicationName) {

    String systemFallback = "SYSTEM-" + applicationName;

    return () -> {
      CurrentActorProvider provider = actorProviders.getIfAvailable();
      if (provider == null) {
        return Optional.of(systemFallback);
      }
      return Optional.of(provider.currentActor().orElse(systemFallback));
    };
  }
}
