package com.portfolio.insuhr.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 애플리케이션 레벨 AES-256-GCM 암·복호화 (설계서 6.8, 10.3).
 *
 * <p>대상: 주민등록번호, 휴대폰, 주소, 수수료 지급계좌, 구독시스템 시크릿.
 *
 * <p><b>저장 형식</b>: {@code {키버전}:{Base64(IV || 암호문||태그)}} — 예 {@code v1:9x8...}
 *
 * <p>키 버전을 암호문에 함께 남기는 이유는 키 회전 때문이다(10.3). v2로 회전해도 v1으로 암호화된 과거 데이터를 그대로 읽을 수 있어야 하므로, 복호화는 암호문이
 * 지목한 버전의 키를 쓴다.
 *
 * <p><b>GCM을 쓰는 이유</b>: 암호화와 동시에 무결성 검증(인증 태그)이 된다. 누군가 DB의 암호문을 조작하면 복호화 시점에 태그 검증이 실패한다. CBC였다면
 * 조용히 깨진 평문이 나온다.
 *
 * <p><b>IV는 매 암호화마다 새로 뽑는다.</b> GCM에서 같은 키로 IV를 재사용하면 평문이 노출되는 치명적 취약점이 된다 — 절대 고정하지 말 것.
 *
 * <p>이 클래스는 JDK 내장 JCA만 쓴다. insuhr-common의 의존성 0 원칙(설계서 4.2)을 지키기 위한 것으로, 외부 암호 라이브러리를 여기에 들이지 않는다.
 */
public final class AesGcmCipher {

  static final String VERSION_DELIMITER = ":";

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final String ALGORITHM = "AES";
  private static final int IV_BYTES = 12; // GCM 권장 96비트
  private static final int TAG_BITS = 128;

  private final Map<String, CryptoKey> keysByVersion;
  private final CryptoKey currentKey;
  private final SecureRandom random = new SecureRandom();

  /**
   * @param currentKey 암호화에 쓸 현재 키
   * @param retiredKeys 회전으로 물러났지만 과거 암호문 복호화에 여전히 필요한 키들
   */
  public AesGcmCipher(CryptoKey currentKey, CryptoKey... retiredKeys) {
    this.currentKey = Objects.requireNonNull(currentKey, "currentKey");
    Map<String, CryptoKey> keys = new LinkedHashMap<>();
    keys.put(currentKey.version(), currentKey);
    for (CryptoKey retired : retiredKeys) {
      keys.put(retired.version(), retired);
    }
    this.keysByVersion = Map.copyOf(keys);
  }

  /** 평문을 현재 키로 암호화해 {@code {키버전}:{Base64}} 형식으로 돌려준다. null은 null로 통과시킨다. */
  public String encrypt(String plainText) {
    if (plainText == null) {
      return null;
    }
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);

    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(currentKey.key(), ALGORITHM),
          new GCMParameterSpec(TAG_BITS, iv));
      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      byte[] payload = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(cipherText, 0, payload, iv.length, cipherText.length);

      return currentKey.version() + VERSION_DELIMITER + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw new CryptoException("암호화에 실패했습니다.", e);
    }
  }

  /** 암호문을 복호화한다. 암호문에 적힌 키 버전의 키를 사용한다. null은 null로 통과시킨다. */
  public String decrypt(String encrypted) {
    if (encrypted == null) {
      return null;
    }
    int delimiterAt = encrypted.indexOf(VERSION_DELIMITER);
    if (delimiterAt <= 0) {
      throw new CryptoException("키 버전 프리픽스가 없는 암호문입니다.");
    }

    String version = encrypted.substring(0, delimiterAt);
    CryptoKey key = keysByVersion.get(version);
    if (key == null) {
      // 회전 시 물러난 키를 주입에서 빠뜨리면 여기로 온다. 과거 데이터를 못 읽는 상황이므로
      // 조용히 넘기지 않고 키 버전을 밝혀 실패시킨다.
      throw new CryptoException("복호화 키를 찾을 수 없습니다. 키 버전: " + version);
    }

    try {
      byte[] payload = Base64.getDecoder().decode(encrypted.substring(delimiterAt + 1));
      if (payload.length <= IV_BYTES) {
        throw new CryptoException("암호문 길이가 유효하지 않습니다.");
      }
      byte[] iv = new byte[IV_BYTES];
      System.arraycopy(payload, 0, iv, 0, IV_BYTES);

      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key.key(), ALGORITHM),
          new GCMParameterSpec(TAG_BITS, iv));
      byte[] plain = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      // 태그 검증 실패(변조)도 여기로 온다.
      throw new CryptoException("복호화에 실패했습니다.", e);
    }
  }

  /** 암호문이 어떤 키 버전으로 암호화됐는지. 키 회전 배치가 재암호화 대상을 고를 때 쓴다. */
  public String keyVersionOf(String encrypted) {
    Objects.requireNonNull(encrypted, "encrypted");
    int delimiterAt = encrypted.indexOf(VERSION_DELIMITER);
    if (delimiterAt <= 0) {
      throw new CryptoException("키 버전 프리픽스가 없는 암호문입니다.");
    }
    return encrypted.substring(0, delimiterAt);
  }

  public String currentKeyVersion() {
    return currentKey.version();
  }
}
