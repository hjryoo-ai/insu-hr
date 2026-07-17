package com.portfolio.insuhr.domain.crypto;

import com.portfolio.insuhr.common.crypto.AesGcmCipher;
import com.portfolio.insuhr.common.crypto.CryptoKey;
import com.portfolio.insuhr.common.crypto.PepperedHasher;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 암호화 유틸을 빈으로 노출한다 (설계서 6.8, 10.3).
 *
 * <p>유틸 자체는 insuhr-common(의존성 0)에 있고, 스프링 배선만 여기서 한다.
 */
@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfig {

  /**
   * AES-256-GCM 암·복호화기.
   *
   * <p>현재 키로 암호화하고, 회전으로 물러난 키들은 과거 암호문 복호화를 위해 함께 주입한다. 물러난 키를 빠뜨리면 과거 데이터를 못 읽는다.
   */
  @Bean
  public AesGcmCipher aesGcmCipher(CryptoProperties properties) {
    Map<String, String> keys = properties.keys();
    if (keys == null || keys.isEmpty()) {
      throw new IllegalStateException(
          "insuhr.crypto.keys 가 비어 있습니다. 환경변수로 AES 키를 주입하세요 (설계서 10.3).");
    }
    String currentVersion = properties.currentKeyVersion();
    if (currentVersion == null || !keys.containsKey(currentVersion)) {
      throw new IllegalStateException(
          "insuhr.crypto.current-key-version 이 keys 에 없습니다: " + currentVersion);
    }

    CryptoKey current = toKey(currentVersion, keys.get(currentVersion));
    List<CryptoKey> retired = new ArrayList<>();
    keys.forEach(
        (version, encoded) -> {
          if (!version.equals(currentVersion)) {
            retired.add(toKey(version, encoded));
          }
        });

    return new AesGcmCipher(current, retired.toArray(CryptoKey[]::new));
  }

  /** 주민번호 동일인 검사용 해시 (설계서 6.8). */
  @Bean
  public PepperedHasher rrnHasher(CryptoProperties properties) {
    String pepper = properties.pepper();
    if (pepper == null || pepper.isBlank()) {
      throw new IllegalStateException("insuhr.crypto.pepper 가 설정되지 않았습니다. 환경변수로 주입하세요 (설계서 10.3).");
    }
    return new PepperedHasher(pepper.getBytes(StandardCharsets.UTF_8));
  }

  private static CryptoKey toKey(String version, String base64Key) {
    try {
      return new CryptoKey(version, Base64.getDecoder().decode(base64Key));
    } catch (IllegalArgumentException e) {
      // 키 형식이 틀리면 기동 시점에 실패시킨다 — 운영 중에 알게 되면 이미 늦다.
      throw new IllegalStateException("AES 키가 Base64 가 아닙니다. 키 버전=" + version, e);
    }
  }
}
