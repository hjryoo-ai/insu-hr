package com.portfolio.insuhr.domain.emp;

import com.portfolio.insuhr.common.error.ErrorCode;

/** 임직원 도메인 에러코드 (EMP). 설계서 7.1의 {도메인 3자}-{HTTP류 2자}{일련 2자}. */
public enum EmpErrorCode implements ErrorCode {
  NOT_FOUND("EMP-4041", 404, "임직원을 찾을 수 없습니다."),
  APPOINT_NOT_FOUND("EMP-4042", 404, "발령을 찾을 수 없습니다."),
  RECORD_NOT_FOUND("EMP-4043", 404, "인사기록을 찾을 수 없습니다."),

  ALREADY_EMPLOYEE("EMP-4091", 409, "이미 임직원으로 등록된 인물입니다."),
  APPOINT_NOT_DRAFT("EMP-4092", 409, "기안 상태의 발령만 확정할 수 있습니다."),
  /** 설계서 5.5 v1.4: 반영된 과거는 지우지 않는다 — 정정 발령으로만 되돌린다 */
  APPOINT_ALREADY_APPLIED("EMP-4093", 409, "이미 반영된 발령은 취소할 수 없습니다. 정정 발령으로 되돌리십시오."),
  APPOINT_ALREADY_CANCELED("EMP-4094", 409, "이미 취소된 발령입니다."),
  RESIGNED("EMP-4095", 409, "퇴직한 임직원에게는 발령을 낼 수 없습니다."),

  INVALID_APPOINT_DT("EMP-4001", 400, "발령일이 입사일보다 앞설 수 없습니다."),
  ORG_CLOSED("EMP-4002", 400, "폐지된 조직으로 발령할 수 없습니다.");

  private final String code;
  private final int httpStatus;
  private final String defaultMessage;

  EmpErrorCode(String code, int httpStatus, String defaultMessage) {
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
