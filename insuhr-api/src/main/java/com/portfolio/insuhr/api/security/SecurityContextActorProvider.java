package com.portfolio.insuhr.api.security;

import com.portfolio.insuhr.domain.audit.CurrentActorProvider;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 감사 컬럼에 남길 행위자를 SecurityContext에서 읽는다 (설계서 6.2).
 *
 * <p>이 빈은 api 모듈에만 있다 — 배치·릴레이에는 SecurityContext가 없어 등록하지 않으며, 그쪽은 {@code SYSTEM-{모듈명}}으로
 * 폴백한다({@code JpaAuditingConfig} 참조).
 *
 * <p>인증되지 않은 요청(로그인 API 등)에서는 empty를 돌려 같은 폴백을 타게 한다.
 */
@Component
public class SecurityContextActorProvider implements CurrentActorProvider {

  @Override
  public Optional<String> currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return Optional.empty();
    }
    if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
      return Optional.ofNullable(user.loginId());
    }
    return Optional.empty();
  }
}
