package com.portfolio.insuhr.domain.auth;

/** 계정 유형 (공통코드 USER_TYPE). */
public enum UserType {
  /** 사람 계정 — 비밀번호 로그인 */
  HUMAN,
  /** 연계용 기계 계정 — client_credentials형 토큰 발급 (설계서 7.2) */
  SYSTEM
}
