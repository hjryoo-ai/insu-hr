package com.portfolio.insuhr.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 서명 설정.
 *
 * <p>시크릿은 환경변수/외부 시크릿에서 주입한다 — 소스·DB에 저장 금지(설계서 10.3).
 *
 * <p>토큰 수명(TTL)은 여기 두지 않는다. Access 30분/Refresh 14일은 정책값(TB_POLICY_CONFIG)이라 운영 중 바뀔 수 있고, 설계서 13.1이
 * 하드코딩을 금지한다.
 *
 * @param secret HS256 서명 키. 최소 32바이트
 * @param issuer 토큰 발급자 식별자
 */
@ConfigurationProperties(prefix = "insuhr.jwt")
public record JwtProperties(String secret, String issuer) {

  public JwtProperties {
    issuer = (issuer == null || issuer.isBlank()) ? "insuhr" : issuer;
  }
}
