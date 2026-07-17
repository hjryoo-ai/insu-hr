package com.portfolio.insuhr.common.error;

/** 도메인 공통 에러코드 (COM). 도메인 고유 코드는 각 Phase에서 별도 enum으로 추가한다. */
public enum CommonErrorCode implements ErrorCode {
  INVALID_REQUEST("COM-4001", 400, "요청 값이 올바르지 않습니다."),
  UNAUTHORIZED("COM-4011", 401, "인증이 필요합니다."),
  FORBIDDEN("COM-4031", 403, "접근 권한이 없습니다."),
  NOT_FOUND("COM-4041", 404, "대상을 찾을 수 없습니다."),
  CONFLICT("COM-4091", 409, "현재 상태와 충돌하는 요청입니다."),
  INTERNAL_ERROR("COM-5001", 500, "서버 오류가 발생했습니다.");

  private final String code;
  private final int httpStatus;
  private final String defaultMessage;

  CommonErrorCode(String code, int httpStatus, String defaultMessage) {
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
