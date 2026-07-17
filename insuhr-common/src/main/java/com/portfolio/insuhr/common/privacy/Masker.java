package com.portfolio.insuhr.common.privacy;

/**
 * 개인식별정보 마스킹 (설계서 10.2).
 *
 * <p>규칙: 이름 {@code 김*수}, 주민번호 {@code 900101-1******}, 휴대폰 {@code 010-****-1234}, 계좌 뒤 4자리만.
 *
 * <p>목록·상세 응답의 개인식별정보는 <b>항상</b> 이 유틸을 거친 값으로 나간다. 원문은 전용 복호화 엔드포인트에서만 나가며 접근로그가 남는다(설계서 7.1,
 * 10.2).
 *
 * <p>입력이 null이면 null, 형식이 예상과 다르면 <b>더 많이 가리는 쪽</b>으로 처리한다. 마스킹 유틸이 예외를 던지면 호출부가 그것을 회피하려고 원문을 노출하는
 * 길로 빠지기 쉽다.
 */
public final class Masker {

  private static final char MASK = '*';

  private Masker() {}

  /**
   * 이름 마스킹. 가운데를 가린다.
   *
   * <pre>
   * 김민수   → 김*수
   * 김수     → 김*      (2자는 뒤를 가린다)
   * 남궁민수 → 남**수
   * 김        → *        (1자는 통째로)
   * </pre>
   */
  public static String name(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    return switch (trimmed.length()) {
      case 0 -> trimmed;
      case 1 -> String.valueOf(MASK);
      case 2 -> trimmed.charAt(0) + String.valueOf(MASK);
      default ->
          trimmed.charAt(0)
              + String.valueOf(MASK).repeat(trimmed.length() - 2)
              + trimmed.charAt(trimmed.length() - 1);
    };
  }

  /**
   * 주민등록번호 마스킹. 성별자리까지만 남긴다.
   *
   * <pre>
   * 9001011234567  → 900101-1******
   * 900101-1234567 → 900101-1******
   * </pre>
   *
   * 자릿수가 안 맞으면 전체를 가린다.
   */
  public static String rrn(String rrn) {
    if (rrn == null) {
      return null;
    }
    String digits = rrn.replaceAll("[^0-9]", "");
    if (digits.length() != 13) {
      return maskAll(rrn);
    }
    return digits.substring(0, 6) + "-" + digits.charAt(6) + String.valueOf(MASK).repeat(6);
  }

  /**
   * 휴대폰 마스킹. 가운데 국번을 가린다.
   *
   * <pre>
   * 01012341234   → 010-****-1234
   * 010-1234-1234 → 010-****-1234
   * 0212341234    → 02-****-1234   (지역번호 등 10자리)
   * </pre>
   */
  public static String phone(String phone) {
    if (phone == null) {
      return null;
    }
    String digits = phone.replaceAll("[^0-9]", "");
    if (digits.length() < 9 || digits.length() > 11) {
      return maskAll(phone);
    }
    String head =
        digits.length() == 10 && !digits.startsWith("01")
            ? digits.substring(0, 2)
            : digits.substring(0, 3);
    String tail = digits.substring(digits.length() - 4);
    return head + "-" + String.valueOf(MASK).repeat(4) + "-" + tail;
  }

  /**
   * 계좌번호 마스킹. 뒤 4자리만 남긴다.
   *
   * <pre>
   * 110123456789 → ********6789
   * </pre>
   */
  public static String account(String account) {
    if (account == null) {
      return null;
    }
    String digits = account.replaceAll("[^0-9]", "");
    if (digits.length() <= 4) {
      return maskAll(digits);
    }
    return String.valueOf(MASK).repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
  }

  /** 이메일 마스킹. 로컬파트 첫 글자만 남긴다: {@code h***@example.com} */
  public static String email(String email) {
    if (email == null) {
      return null;
    }
    int at = email.indexOf('@');
    if (at <= 0) {
      return maskAll(email);
    }
    String local = email.substring(0, at);
    String domain = email.substring(at);
    if (local.length() == 1) {
      return MASK + domain;
    }
    return local.charAt(0) + String.valueOf(MASK).repeat(local.length() - 1) + domain;
  }

  private static String maskAll(String value) {
    return value.isEmpty() ? value : String.valueOf(MASK).repeat(value.length());
  }
}
