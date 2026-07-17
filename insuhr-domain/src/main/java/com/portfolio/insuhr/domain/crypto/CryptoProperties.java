package com.portfolio.insuhr.domain.crypto;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 암호화 키 설정 (설계서 10.3).
 *
 * <p>키·pepper는 환경변수/외부 시크릿(운영 가정: KMS/Vault)에서 주입한다 — 소스·DB에 저장 금지.
 *
 * @param currentKeyVersion 새 암호화에 쓸 키 버전. 예: {@code v1}
 * @param keys 키 버전 → Base64(32바이트). 회전으로 물러난 키도 과거 암호문 복호화를 위해 남겨둔다
 * @param pepper 주민번호 해시용 pepper (6.8)
 */
@ConfigurationProperties(prefix = "insuhr.crypto")
public record CryptoProperties(String currentKeyVersion, Map<String, String> keys, String pepper) {}
