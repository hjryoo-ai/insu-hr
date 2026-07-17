package com.portfolio.insuhr.domain.org;

import com.portfolio.insuhr.common.error.ErrorCode;

/** 조직 도메인 에러코드 (ORG). 설계서 7.1의 {도메인 3자}-{HTTP류 2자}{일련 2자}. */
public enum OrgErrorCode implements ErrorCode {
  NOT_FOUND("ORG-4041", 404, "조직을 찾을 수 없습니다."),
  DUPLICATE_CODE("ORG-4091", 409, "이미 사용 중인 조직코드입니다."),
  /** 설계서 7.2: 하위조직/소속인원 존재 시 409 */
  HAS_CHILDREN("ORG-4092", 409, "하위 조직이 있어 폐지할 수 없습니다."),
  HAS_MEMBERS("ORG-4093", 409, "소속 인원이 있어 폐지할 수 없습니다."),
  INVALID_HIERARCHY("ORG-4001", 400, "조직 계층이 올바르지 않습니다."),
  ALREADY_CLOSED("ORG-4094", 409, "이미 폐지된 조직입니다.");

  private final String code;
  private final int httpStatus;
  private final String defaultMessage;

  OrgErrorCode(String code, int httpStatus, String defaultMessage) {
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
