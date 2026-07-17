package com.portfolio.insuhr.domain.auth;

/** 계정 상태 (공통코드 USER_STATUS). */
public enum UserStatus {
  ACTIVE,
  /** 로그인 연속 실패 임계값 도달 (설계서 10.1) */
  LOCKED,
  DISABLED
}
