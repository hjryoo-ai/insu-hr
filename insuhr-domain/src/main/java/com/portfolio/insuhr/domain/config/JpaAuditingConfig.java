package com.portfolio.insuhr.domain.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** BaseEntity의 CREATED_BY/UPDATED_BY를 채우는 감사 설정 (설계서 6.2). */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

  static final String SYSTEM_ACTOR = "SYSTEM";

  /**
   * 현재 행위자를 돌려준다.
   *
   * <p>Phase 0에서는 인증이 없으므로 SYSTEM 고정. Phase 1에서 SecurityContext의 로그인 ID를 읽도록 교체한다. 배치/릴레이처럼 인증
   * 컨텍스트가 없는 실행 모듈은 그 뒤로도 SYSTEM으로 남는다.
   */
  @Bean
  public AuditorAware<String> auditorAware() {
    return () -> Optional.of(SYSTEM_ACTOR);
  }
}
