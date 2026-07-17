package com.portfolio.insuhr.common.crypto;

import java.util.Objects;

/**
 * 버전이 붙은 암호화 키 (설계서 10.3).
 *
 * <p>키 회전을 위해 암호문 앞에 키 버전을 프리픽스로 남긴다. 새 키로 회전해도 과거 암호문을 계속 복호화할 수 있어야 하므로, 복호화는 암호문에 적힌 버전의 키를 찾아
 * 쓰고 암호화는 항상 현재 키로 한다.
 *
 * @param version 키 버전 식별자. 예: {@code v1}
 * @param key AES-256 키 바이트(32바이트)
 */
public record CryptoKey(String version, byte[] key) {

  private static final int AES_256_KEY_BYTES = 32;

  public CryptoKey {
    Objects.requireNonNull(version, "version");
    Objects.requireNonNull(key, "key");
    if (version.isBlank()) {
      throw new IllegalArgumentException("키 버전이 비어 있습니다.");
    }
    if (version.contains(AesGcmCipher.VERSION_DELIMITER)) {
      throw new IllegalArgumentException(
          "키 버전에 구분자 '" + AesGcmCipher.VERSION_DELIMITER + "' 를 쓸 수 없습니다: " + version);
    }
    if (key.length != AES_256_KEY_BYTES) {
      throw new IllegalArgumentException(
          "AES-256 키는 " + AES_256_KEY_BYTES + "바이트여야 합니다. 실제: " + key.length);
    }
    key = key.clone();
  }

  @Override
  public byte[] key() {
    return key.clone();
  }
}
