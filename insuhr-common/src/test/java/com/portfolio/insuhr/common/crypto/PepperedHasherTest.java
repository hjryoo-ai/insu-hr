package com.portfolio.insuhr.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 검색용 SHA-256 + pepper 해시 (설계서 6.8). */
class PepperedHasherTest {

  private final PepperedHasher hasher =
      new PepperedHasher("test-pepper".getBytes(StandardCharsets.UTF_8));

  @Test
  @DisplayName("같은 평문은 항상 같은 해시 — 동일인 검사가 가능해야 한다")
  void isDeterministic() {
    // 이게 결정적이지 않으면 RRN_HASH UNIQUE 제약도, 중복 인물 검사(설계서 5.2)도 성립하지 않는다.
    assertThat(hasher.hash("900101-1234567")).isEqualTo(hasher.hash("900101-1234567"));
  }

  @Test
  @DisplayName("TB_PERSON.RRN_HASH VARCHAR2(64) 에 맞는 64자 hex")
  void producesSixtyFourCharHex() {
    assertThat(hasher.hash("900101-1234567")).hasSize(64).matches("[0-9a-f]{64}");
  }

  @Test
  @DisplayName("평문이 다르면 해시가 다르다")
  void differentInputProducesDifferentHash() {
    assertThat(hasher.hash("900101-1234567")).isNotEqualTo(hasher.hash("900101-1234568"));
  }

  @Test
  @DisplayName("pepper가 다르면 같은 평문도 다른 해시 — pepper 없이는 역산 불가")
  void pepperChangesResult() {
    PepperedHasher other = new PepperedHasher("other-pepper".getBytes(StandardCharsets.UTF_8));

    // 주민번호는 경우의 수가 좁아 순수 SHA-256이면 전수 대입으로 역산된다.
    // DB가 통째로 유출돼도 pepper가 없으면 못 푸는 것이 이 단언의 의미.
    assertThat(hasher.hash("900101-1234567")).isNotEqualTo(other.hash("900101-1234567"));
  }

  @Test
  @DisplayName("해시에 평문이 남지 않는다")
  void hashDoesNotLeakPlainText() {
    assertThat(hasher.hash("900101-1234567")).doesNotContain("900101");
  }

  @Test
  void matchesComparesCorrectly() {
    String hash = hasher.hash("900101-1234567");

    assertThat(hasher.matches("900101-1234567", hash)).isTrue();
    assertThat(hasher.matches("900101-7654321", hash)).isFalse();
    assertThat(hasher.matches(null, hash)).isFalse();
    assertThat(hasher.matches("900101-1234567", null)).isFalse();
  }
}
