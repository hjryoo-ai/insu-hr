package com.portfolio.insuhr.domain.leave;

import com.portfolio.insuhr.common.error.ErrorCode;

/** 휴가 도메인 에러코드. 임직원 도메인의 하위라 EMP 접두를 쓴다 (설계서 7.1). */
public enum LeaveErrorCode implements ErrorCode {
  REQUEST_NOT_FOUND("EMP-4044", 404, "휴가 신청을 찾을 수 없습니다."),
  GRANT_NOT_FOUND("EMP-4045", 404, "연차 부여 내역을 찾을 수 없습니다."),

  NOT_PENDING("EMP-4096", 409, "신청 상태의 휴가만 처리할 수 있습니다."),
  INSUFFICIENT_BALANCE("EMP-4097", 409, "연차 잔여일수가 부족합니다."),
  ALREADY_GRANTED("EMP-4098", 409, "이미 해당 연도 연차가 부여되었습니다."),

  INVALID_PERIOD("EMP-4003", 400, "휴가 종료일이 시작일보다 앞설 수 없습니다."),
  INVALID_DAYS("EMP-4004", 400, "휴가 일수는 0보다 커야 합니다.");

  private final String code;
  private final int httpStatus;
  private final String defaultMessage;

  LeaveErrorCode(String code, int httpStatus, String defaultMessage) {
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
