package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.common.error.ErrorCode;

/** 설계사 도메인 에러코드 (AGT). 설계서 7.1의 {도메인 3자}-{HTTP류 2자}{일련 2자}. */
public enum AgentErrorCode implements ErrorCode {
  NOT_FOUND("AGT-4041", 404, "설계사를 찾을 수 없습니다."),

  ALREADY_AGENT("AGT-4091", 409, "이미 설계사로 등록된 인물입니다."),
  ILLEGAL_TRANSITION("AGT-4092", 409, "현재 상태에서 허용되지 않는 위촉 전이입니다."),
  REAPPOINT_COOLDOWN("AGT-4093", 409, "재위촉 제한기간이 지나지 않았습니다."),
  REAPPOINT_FORBIDDEN("AGT-4094", 409, "재위촉이 금지된 해촉사유입니다."),
  ORG_CLOSED("AGT-4095", 409, "폐지되었거나 개설 전인 조직에는 소속시킬 수 없습니다."),
  RECRUITER_CYCLE("AGT-4096", 409, "도입자 지정이 계보에 순환을 만듭니다."),

  EDU_HOURS_INSUFFICIENT("AGT-4002", 400, "교육 이수시간이 최소 기준에 미달합니다."),

  /** 설계서 5.3/7.3 — 위촉 요건 미충족은 예외 하나가 아니라 사유 배열을 담은 422다. */
  REQUIREMENT_NOT_MET("AGT-4221", 422, "위촉 요건이 충족되지 않았습니다."),
  /** 설계서 5.4 v1.6 — 모집자격 미회복 상태에서 정지해제 시도. 사유 배열 동반 422 */
  RESUME_NOT_ELIGIBLE("AGT-4222", 422, "모집자격이 회복되지 않아 정지해제할 수 없습니다.");

  private final String code;
  private final int httpStatus;
  private final String defaultMessage;

  AgentErrorCode(String code, int httpStatus, String defaultMessage) {
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
