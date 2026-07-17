package com.portfolio.insuhr.domain.person;

import com.portfolio.insuhr.common.error.ErrorCode;

/** 인물 도메인 에러코드 (PER). 설계서 7.1의 {도메인 3자}-{HTTP류 2자}{일련 2자}. */
public enum PersonErrorCode implements ErrorCode {
  NOT_FOUND("PER-4041", 404, "인물을 찾을 수 없습니다."),
  /** 복호화 시 사유 입력은 필수다 (설계서 10.2). */
  PURPOSE_REQUIRED("PER-4001", 400, "개인정보 열람 사유를 입력해야 합니다."),
  INVALID_RRN("PER-4002", 400, "주민등록번호 형식이 올바르지 않습니다.");

  private final String code;
  private final int httpStatus;
  private final String defaultMessage;

  PersonErrorCode(String code, int httpStatus, String defaultMessage) {
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
