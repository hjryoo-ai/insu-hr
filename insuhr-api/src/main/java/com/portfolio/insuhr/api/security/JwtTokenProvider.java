package com.portfolio.insuhr.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 발급·검증 (설계서 10.1).
 *
 * <p>권한(PERM_CD)을 클레임에 실어 보내므로 요청마다 DB를 다시 읽지 않는다. 대신 권한을 회수해도 기존 Access 토큰이 만료(기본 30분)될 때까지는 유효하다
 * — Access 수명을 짧게 두는 이유가 이것이다.
 *
 * <p>직렬화는 jjwt-gson이 담당한다(설계서 3.0). jjwt-jackson을 쓰면 Jackson 2가 클래스패스에 올라와 Boot 4 기본인 Jackson 3과
 * 공존하게 된다.
 */
@Component
public class JwtTokenProvider {

  /** 권한 목록 클레임 키. */
  static final String CLAIM_PERMISSIONS = "perms";

  /** 토큰 종류 클레임 키 — access 토큰을 refresh 자리에 쓰는 것을 막는다. */
  static final String CLAIM_TOKEN_TYPE = "typ";

  static final String TOKEN_TYPE_ACCESS = "access";

  private static final int MIN_SECRET_BYTES = 32;

  private final SecretKey signingKey;
  private final String issuer;

  public JwtTokenProvider(JwtProperties properties) {
    byte[] secret = toSecretBytes(properties.secret());
    this.signingKey = Keys.hmacShaKeyFor(secret);
    this.issuer = properties.issuer();
  }

  private static byte[] toSecretBytes(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException(
          "insuhr.jwt.secret 이 설정되지 않았습니다. 환경변수 INSUHR_JWT_SECRET 로 주입하세요 (설계서 10.3).");
    }
    byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      // HS256 키가 짧으면 서명 강도가 무너진다. 기동 시점에 실패시켜 배포 전에 알게 한다.
      throw new IllegalStateException(
          "insuhr.jwt.secret 은 최소 " + MIN_SECRET_BYTES + "바이트여야 합니다. 실제: " + bytes.length);
    }
    return bytes;
  }

  /**
   * Access 토큰 발급.
   *
   * @param ttl 유효시간. 정책값(ACCESS_TOKEN_TTL_MINUTES)에서 온 값을 받는다 — 하드코딩 금지(설계서 13.1)
   */
  public String issueAccessToken(
      Long userId, String loginId, Set<String> permissions, Duration ttl) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(issuer)
        .subject(String.valueOf(userId))
        .claim("loginId", loginId)
        .claim(CLAIM_PERMISSIONS, List.copyOf(permissions))
        .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(signingKey)
        .compact();
  }

  /**
   * Access 토큰을 검증하고 인증 주체를 돌려준다.
   *
   * @throws JwtException 서명 불일치·만료·형식 오류
   */
  public AuthenticatedUser parseAccessToken(String token) {
    Claims claims =
        Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .getPayload();

    // refresh 토큰을 Authorization 헤더에 넣어 우회하는 것을 막는다.
    String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
    if (!TOKEN_TYPE_ACCESS.equals(tokenType)) {
      throw new JwtException("access 토큰이 아닙니다. typ=" + tokenType);
    }

    @SuppressWarnings("unchecked")
    List<String> permissions = claims.get(CLAIM_PERMISSIONS, List.class);

    return new AuthenticatedUser(
        Long.valueOf(claims.getSubject()),
        claims.get("loginId", String.class),
        permissions == null ? Set.of() : Set.copyOf(permissions));
  }
}
