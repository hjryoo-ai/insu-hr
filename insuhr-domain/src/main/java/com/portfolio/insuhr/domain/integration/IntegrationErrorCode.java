package com.portfolio.insuhr.domain.integration;

import com.portfolio.insuhr.common.error.ErrorCode;

/** 대외연계 도메인 에러코드 (IFC). 설계서 7.1의 {도메인 3자}-{HTTP류 2자}{일련 2자}. */
public enum IntegrationErrorCode implements ErrorCode {
  SUBSCRIBER_NOT_FOUND("IFC-4041", 404, "구독 시스템을 찾을 수 없습니다."),
  OUTBOX_NOT_FOUND("IFC-4042", 404, "이벤트를 찾을 수 없습니다."),

  DUPLICATE_SUBSCRIBER("IFC-4091", 409, "이미 등록된 구독 시스템 코드입니다.");

  private final String code;
  private final int httpStatus;
  private final String defaultMessage;

  IntegrationErrorCode(String code, int httpStatus, String defaultMessage) {
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
