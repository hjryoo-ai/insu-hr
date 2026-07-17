package com.portfolio.insuhr.common.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 개인식별정보 마스킹 (설계서 10.2). */
class MaskerTest {

  @Nested
  @DisplayName("이름")
  class Name {

    @ParameterizedTest
    @CsvSource({
      "김민수, 김*수",
      "남궁민수, 남**수",
      "김수, 김*",
      "김, *",
    })
    void masksMiddle(String raw, String expected) {
      assertThat(Masker.name(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("설계서 예시 그대로 (김*수)")
    void matchesSpecExample() {
      assertThat(Masker.name("김민수")).isEqualTo("김*수");
    }

    @Test
    void handlesNull() {
      assertThat(Masker.name(null)).isNull();
    }
  }

  @Nested
  @DisplayName("주민등록번호")
  class Rrn {

    @ParameterizedTest
    @CsvSource({
      "9001011234567, 900101-1******",
      "900101-1234567, 900101-1******",
    })
    void keepsBirthAndGenderDigitOnly(String raw, String expected) {
      assertThat(Masker.rrn(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("설계서 예시 그대로 (900101-1******)")
    void matchesSpecExample() {
      assertThat(Masker.rrn("9001011234567")).isEqualTo("900101-1******");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "90010112345678901"})
    @DisplayName("자릿수가 안 맞으면 전체를 가린다 — 애매하면 더 가리는 쪽")
    void masksEverythingOnUnexpectedFormat(String raw) {
      assertThat(Masker.rrn(raw)).matches("\\*+").hasSameSizeAs(raw);
    }
  }

  @Nested
  @DisplayName("휴대폰")
  class Phone {

    @ParameterizedTest
    @CsvSource({
      "01012341234, 010-****-1234",
      "010-1234-1234, 010-****-1234",
      "0212341234, 02-****-1234",
    })
    void masksMiddleDigits(String raw, String expected) {
      assertThat(Masker.phone(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("설계서 예시 그대로 (010-****-1234)")
    void matchesSpecExample() {
      assertThat(Masker.phone("01012341234")).isEqualTo("010-****-1234");
    }
  }

  @Nested
  @DisplayName("계좌번호")
  class Account {

    @Test
    @DisplayName("뒤 4자리만 남긴다")
    void keepsLastFourOnly() {
      assertThat(Masker.account("110123456789")).isEqualTo("********6789");
      assertThat(Masker.account("110-123-456789")).isEqualTo("********6789");
    }

    @Test
    @DisplayName("4자리 이하면 전체를 가린다")
    void masksEverythingWhenTooShort() {
      assertThat(Masker.account("1234")).isEqualTo("****");
    }
  }

  @Nested
  @DisplayName("이메일")
  class Email {

    @Test
    void keepsFirstCharOfLocalPart() {
      assertThat(Masker.email("hongildong@example.com")).isEqualTo("h*********@example.com");
    }

    @Test
    @DisplayName("@ 가 없으면 전체를 가린다")
    void masksEverythingWithoutAtSign() {
      assertThat(Masker.email("not-an-email")).matches("\\*+");
    }
  }
}
