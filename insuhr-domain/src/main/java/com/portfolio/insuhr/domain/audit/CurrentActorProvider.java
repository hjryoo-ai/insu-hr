package com.portfolio.insuhr.domain.audit;

import java.util.Optional;

/**
 * 현재 행위자(감사 컬럼 CREATED_BY/UPDATED_BY에 남을 값)를 제공한다.
 *
 * <p>도메인이 SecurityContext를 알면 안 되므로 인터페이스로 끊는다. api 모듈이 SecurityContext 기반 구현을 등록하고, <b>배치·릴레이는
 * 등록하지 않는다</b> — 그쪽에는 인증 컨텍스트가 없기 때문이다. 구현이 없으면 {@code SYSTEM-{모듈명}} 으로 폴백한다(설계서 13.2 v1.2).
 *
 * <p>폴백이 없으면 Phase 7에서 배치가 감사컬럼 NOT NULL 제약에 걸려 넘어진다.
 */
public interface CurrentActorProvider {

  /** 인증된 행위자. 없으면 empty — 호출부가 폴백한다. */
  Optional<String> currentActor();
}
