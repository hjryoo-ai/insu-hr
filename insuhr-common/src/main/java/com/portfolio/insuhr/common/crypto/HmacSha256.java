package com.portfolio.insuhr.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 서명 (설계서 9.2 — 웹훅 전송 서명).
 *
 * <p>릴레이가 전송 바이트에 서명하고 수신측이 같은 시크릿으로 재계산해 위·변조를 검증한다. 서명 입력은 {@code timestamp + "." + body}로, 본문뿐
 * 아니라 타임스탬프까지 덮어야 캡처된 요청의 무한 재생(리플레이)을 막는다.
 *
 * <p>JDK 내장 JCA만 쓴다 — insuhr-common의 의존성 0 원칙(설계서 4.2). {@link AesGcmCipher}와 같은 자리.
 */
public final class HmacSha256 {

  private static final String ALGORITHM = "HmacSHA256";

  private HmacSha256() {}

  /** {@code message}를 {@code secret}으로 HMAC-SHA256 서명하고 소문자 hex 문자열로 돌려준다. */
  public static String hex(String secret, String message) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
      byte[] signature = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(signature);
    } catch (GeneralSecurityException e) {
      throw new CryptoException("HMAC 서명에 실패했습니다.", e);
    }
  }
}
