package com.portfolio.insuhr.api.security;

import java.util.Set;

/**
 * 인증된 요청의 주체.
 *
 * @param userId TB_USER.USER_ID
 * @param loginId 로그인 ID. 감사 컬럼(CREATED_BY/UPDATED_BY)에 기록된다
 * @param permissions PERM_CD 집합 ({리소스}.{행위})
 */
public record AuthenticatedUser(Long userId, String loginId, Set<String> permissions) {

  public AuthenticatedUser {
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }
}
