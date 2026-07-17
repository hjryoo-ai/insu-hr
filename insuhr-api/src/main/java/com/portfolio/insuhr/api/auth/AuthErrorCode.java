package com.portfolio.insuhr.api.auth;

import com.portfolio.insuhr.common.error.ErrorCode;

/** 인증 도메인 에러코드 (AUT). 설계서 7.1의 {도메인 3자}-{HTTP류 2자}{일련 2자}. */
public enum AuthErrorCode implements ErrorCode {

  /**
   * 로그인 실패. 아이디가 없는 경우와 비밀번호가 틀린 경우를 같은 코드·같은 메시지로 응답한다 — 구분해서 알려주면 어떤 아이디가 존재하는지 흘리는 셈이다(계정 열거
   * 공격).
   */
  INVALID_CREDENTIALS("AUT-4011", 401, "아이디 또는 비밀번호가 올바르지 않습니다."),
  ACCOUNT_LOCKED("AUT-4012", 401, "계정이 잠겼습니다. 관리자에게 문의하세요."),
  ACCOUNT_DISABLED("AUT-4013", 401, "사용할 수 없는 계정입니다."),
  INVALID_REFRESH_TOKEN("AUT-4014", 401, "유효하지 않은 refresh 토큰입니다.");

  private final String code;
  private final int httpStatus;
  private final String defaultMessage;

  AuthErrorCode(String code, int httpStatus, String defaultMessage) {
    this.code = code;
    this.httpStatus = httpStatus;
    this.defaultMessage = defaultMessage;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int httpStatus() {
    return httpStatus;
  }

  @Override
  public String defaultMessage() {
    return defaultMessage;
  }
}
