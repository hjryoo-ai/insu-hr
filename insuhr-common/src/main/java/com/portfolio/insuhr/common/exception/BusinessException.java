package com.portfolio.insuhr.common.exception;

import com.portfolio.insuhr.common.error.ErrorCode;
import com.portfolio.insuhr.common.error.ErrorDetail;
import java.util.List;

/**
 * 업무 규칙 위반 예외. 도메인/애플리케이션 계층이 던지고 api 모듈의 핸들러가 표준 envelope으로 변환한다.
 *
 * <p>스택트레이스를 남기지 않는다 — 업무 규칙 위반은 장애가 아니라 정상적인 분기다.
 */
public class BusinessException extends RuntimeException {

  private final transient ErrorCode errorCode;
  private final transient List<ErrorDetail> details;

  public BusinessException(ErrorCode errorCode) {
    this(errorCode, errorCode.defaultMessage(), List.of());
  }

  public BusinessException(ErrorCode errorCode, String message) {
    this(errorCode, message, List.of());
  }

  public BusinessException(ErrorCode errorCode, List<ErrorDetail> details) {
    this(errorCode, errorCode.defaultMessage(), details);
  }

  public BusinessException(ErrorCode errorCode, String message, List<ErrorDetail> details) {
    super(message, null, false, false);
    this.errorCode = errorCode;
    this.details = List.copyOf(details);
  }

  public ErrorCode errorCode() {
    return errorCode;
  }

  public List<ErrorDetail> details() {
    return details;
  }
}
