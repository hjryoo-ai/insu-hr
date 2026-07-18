package com.portfolio.insuhr.domain.policy;

/**
 * 정책값 키 (설계서 13.1, 부록 A).
 *
 * <p>법령·사규에 따라 달라지는 수치는 코드에 박지 않고 TB_POLICY_CONFIG에서 읽는다. 키 자체를 enum으로 두는 이유는 오타로 조용히 기본값이 쓰이는 일을
 * 막기 위함이다 — 문자열 리터럴이면 {@code "CONT_EDU_CYCLE_MONHTS"} 같은 오타를 컴파일러가 못 잡는다.
 */
public enum PolicyKey {

  /** 보수교육 이수 주기(개월). 설계서 기본값 24 */
  CONT_EDU_CYCLE_MONTHS,
  /** 등록교육 최소 이수시간 */
  REG_EDU_MIN_HOURS,
  /** 재정보증 최소 보증금액(원) */
  MIN_GRNT_AMT,
  /** 해촉 후 재위촉 제한기간(개월). 설계서 5.3 기본값 6 */
  REAPPOINT_COOLDOWN_MONTHS,
  /** 재위촉 시 과거 등록교육 재사용 여부(Y/N). 설계서 5.4 v1.6 기본값 Y */
  REG_EDU_REUSE_ON_REAPPOINT,
  /** 개인정보 보존기간(년) */
  PRIVACY_RETENTION_YEARS,

  /** 비밀번호 변경 주기(일) */
  PWD_EXPIRE_DAYS,
  /** 비밀번호 최소 길이 */
  PWD_MIN_LENGTH,
  /** 재사용 금지할 최근 비밀번호 개수 */
  PWD_REUSE_BLOCK_CNT,
  /** 로그인 연속 실패 잠금 임계값 */
  LOGIN_FAIL_LOCK_CNT,
  /** Access 토큰 유효시간(분) */
  ACCESS_TOKEN_TTL_MINUTES,
  /** Refresh 토큰 유효기간(일) */
  REFRESH_TOKEN_TTL_DAYS
}
