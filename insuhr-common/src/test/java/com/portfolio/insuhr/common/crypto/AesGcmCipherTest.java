package com.portfolio.insuhr.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AES-256-GCM 암·복호화 (설계서 6.8, 10.3). */
class AesGcmCipherTest {

  private static final CryptoKey V1 = key("v1", 'a');
  private static final CryptoKey V2 = key("v2", 'b');

  private final AesGcmCipher cipher = new AesGcmCipher(V1);

  private static CryptoKey key(String version, char fill) {
    byte[] raw = new byte[32];
    java.util.Arrays.fill(raw, (byte) fill);
    return new CryptoKey(version, raw);
  }

  @Test
  @DisplayName("암호화한 값을 복호화하면 원문이 나온다")
  void roundTrip() {
    String plain = "900101-1234567";
    assertThat(cipher.decrypt(cipher.encrypt(plain))).isEqualTo(plain);
  }

  @Test
  @DisplayName("암호문에 키 버전이 프리픽스로 붙는다")
  void encryptsWithKeyVersionPrefix() {
    assertThat(cipher.encrypt("hello")).startsWith("v1:");
    assertThat(cipher.keyVersionOf(cipher.encrypt("hello"))).isEqualTo("v1");
  }

  @Test
  @DisplayName("같은 평문이라도 매번 다른 암호문이 나온다 (IV 재사용 금지)")
  void producesDifferentCipherTextForSamePlainText() {
    String plain = "900101-1234567";

    // GCM에서 같은 키로 IV를 재사용하면 평문이 노출된다. IV가 매번 새로 뽑히는지 확인한다.
    // 이게 깨지면 암호화가 사실상 무력화되므로 회귀 방지 가치가 큰 단언이다.
    assertThat(cipher.encrypt(plain)).isNotEqualTo(cipher.encrypt(plain));
  }

  @Test
  @DisplayName("암호문에 평문이 남지 않는다")
  void cipherTextDoesNotLeakPlainText() {
    assertThat(cipher.encrypt("900101-1234567")).doesNotContain("900101");
  }

  @Test
  @DisplayName("null은 null로 통과한다")
  void passesNullThrough() {
    assertThat(cipher.encrypt(null)).isNull();
    assertThat(cipher.decrypt(null)).isNull();
  }

  @Test
  @DisplayName("빈 문자열도 왕복한다")
  void roundTripsEmptyString() {
    assertThat(cipher.decrypt(cipher.encrypt(""))).isEmpty();
  }

  @Test
  @DisplayName("키를 회전해도 과거 키로 만든 암호문을 복호화한다")
  void decryptsWithRetiredKeyAfterRotation() {
    String encryptedWithV1 = new AesGcmCipher(V1).encrypt("900101-1234567");

    // v2로 회전. v1은 물러났지만 과거 데이터 복호화를 위해 계속 주입한다.
    AesGcmCipher rotated = new AesGcmCipher(V2, V1);

    assertThat(rotated.decrypt(encryptedWithV1)).isEqualTo("900101-1234567");
    // 새로 암호화하는 것은 현재 키(v2)로
    assertThat(rotated.encrypt("x")).startsWith("v2:");
    assertThat(rotated.currentKeyVersion()).isEqualTo("v2");
  }

  @Test
  @DisplayName("물러난 키를 주입하지 않으면 과거 암호문 복호화가 실패한다")
  void failsWhenRetiredKeyIsMissing() {
    String encryptedWithV1 = new AesGcmCipher(V1).encrypt("secret");

    // v1을 빠뜨린 채 회전한 상황. 조용히 넘어가면 과거 데이터를 못 읽는 걸 늦게 안다.
    assertThatThrownBy(() -> new AesGcmCipher(V2).decrypt(encryptedWithV1))
        .isInstanceOf(CryptoException.class)
        .hasMessageContaining("v1");
  }

  @Test
  @DisplayName("암호문이 변조되면 복호화가 실패한다 (GCM 인증 태그)")
  void failsOnTamperedCipherText() {
    String encrypted = cipher.encrypt("900101-1234567");
    String body = encrypted.substring(encrypted.indexOf(':') + 1);

    byte[] payload = Base64.getDecoder().decode(body);
    payload[payload.length - 1] ^= 0x01; // 마지막 바이트 1비트 뒤집기
    String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

    // CBC였다면 조용히 깨진 평문이 나왔을 것이다. GCM은 태그 검증으로 잡아낸다.
    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(CryptoException.class);
  }

  @Test
  @DisplayName("키 버전 프리픽스가 없는 암호문은 거부한다")
  void rejectsCipherTextWithoutVersionPrefix() {
    String noPrefix =
        Base64.getEncoder().encodeToString("whatever".getBytes(StandardCharsets.UTF_8));
    assertThatThrownBy(() -> cipher.decrypt(noPrefix)).isInstanceOf(CryptoException.class);
  }

  @Test
  @DisplayName("AES-256 키가 아니면 생성 시점에 거부한다")
  void rejectsWrongKeyLength() {
    assertThatThrownBy(() -> new CryptoKey("v1", new byte[16]))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32");
  }

  @Test
  @DisplayName("키 버전에 구분자를 쓰면 거부한다")
  void rejectsVersionContainingDelimiter() {
    // 'v:1' 같은 버전을 허용하면 복호화 시 프리픽스 파싱이 어긋난다.
    assertThatThrownBy(() -> new CryptoKey("v:1", new byte[32]))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
