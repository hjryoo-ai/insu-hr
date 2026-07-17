package com.portfolio.insuhr.domain.policy;

import com.portfolio.insuhr.common.error.CommonErrorCode;
import com.portfolio.insuhr.common.exception.BusinessException;

/**
 * 정책값 조회 실패.
 *
 * <p>업무 규칙 위반이 아니라 설정 누락이므로 500으로 나간다. 값이 없을 때 조용히 기본값을 쓰는 대신 이걸 던지는 이유는 설계서 13.1의 하드코딩 금지 원칙 때문이다
 * — 폴백을 두면 시드 누락을 배포 후에야 알게 된다.
 */
public class PolicyNotFoundException extends BusinessException {

  public PolicyNotFoundException(String message) {
    super(CommonErrorCode.INTERNAL_ERROR, message);
  }

  public PolicyNotFoundException(String message, Throwable cause) {
    super(CommonErrorCode.INTERNAL_ERROR, message);
    initCause(cause);
  }
}
