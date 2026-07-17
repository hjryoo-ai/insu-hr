package com.portfolio.insuhr.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 검색용 SHA-256 + pepper 해시 (설계서 6.8).
 *
 * <p>용도는 하나 — 주민등록번호로 <b>동일인 존재 여부를 찾는 것</b>(설계서 5.2). AES-GCM 암호문은 IV가 매번 달라 같은 값이라도 암호문이 달라지므로 등치
 * 검색이 불가능하다. 그래서 결정적(deterministic) 해시를 별도 컬럼(RRN_HASH)에 두고 UNIQUE 제약과 검색에 쓴다.
 *
 * <p><b>pepper가 필요한 이유</b>: 주민등록번호는 경우의 수가 좁아서 순수 SHA-256이면 전수 대입으로 원문을 역산할 수 있다. DB가 통째로 유출돼도
 * pepper(코드·DB 밖, 환경변수/KMS에 있는 비밀값)가 없으면 역산이 불가능하다. salt와 달리 값마다 다르지 않고 시스템 전체에 하나인데, 이는 등치 검색이
 * 가능해야 하기 때문이다.
 *
 * <p><b>비밀번호에는 쓰지 말 것.</b> SHA-256은 빠른 해시라 비밀번호 저장에는 부적합하다 — BCrypt를 쓴다(설계서 10.1).
 */
public final class PepperedHasher {

  private static final String ALGORITHM = "SHA-256";

  private final byte[] pepper;

  /**
   * @param pepper 환경변수/외부 시크릿에서 주입. 소스·DB에 저장 금지 (설계서 10.3)
   */
  public PepperedHasher(byte[] pepper) {
    Objects.requireNonNull(pepper, "pepper");
    if (pepper.length == 0) {
      throw new IllegalArgumentException("pepper가 비어 있습니다.");
    }
    this.pepper = pepper.clone();
  }

  /** 평문의 pepper 적용 SHA-256을 소문자 hex(64자)로 돌려준다. TB_PERSON.RRN_HASH VARCHAR2(64)에 대응. */
  public String hash(String plainText) {
    Objects.requireNonNull(plainText, "plainText");
    try {
      MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
      digest.update(plainText.getBytes(StandardCharsets.UTF_8));
      digest.update(pepper);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new CryptoException("해시 알고리즘을 사용할 수 없습니다: " + ALGORITHM, e);
    }
  }

  /** 평문이 주어진 해시와 일치하는지. 타이밍 공격을 피하려 상수시간 비교를 쓴다. */
  public boolean matches(String plainText, String expectedHash) {
    if (plainText == null || expectedHash == null) {
      return false;
    }
    return MessageDigest.isEqual(
        hash(plainText).getBytes(StandardCharsets.UTF_8),
        expectedHash.getBytes(StandardCharsets.UTF_8));
  }
}
