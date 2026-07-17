package com.portfolio.insuhr.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Refresh 토큰의 저장용 해시.
 *
 * <p>DB에는 토큰 원문을 절대 넣지 않는다 — DB가 유출돼도 그것만으로 세션을 탈취할 수 없어야 한다(설계서 10.1).
 *
 * <p>pepper 없는 순수 SHA-256으로 충분하다. 토큰이 256비트 난수라 전수 대입이 불가능하기 때문이며, 경우의 수가 좁아 pepper가 필요한 주민번호
 * 해시(설계서 6.8, {@code PepperedHasher})와는 사정이 다르다.
 */
final class TokenHasher {

  private TokenHasher() {}

  static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
    }
  }
}
