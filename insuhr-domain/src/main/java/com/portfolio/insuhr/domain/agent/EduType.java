package com.portfolio.insuhr.domain.agent;

/**
 * 설계사 교육 유형 (설계서 부록 A: EDU_TYPE).
 *
 * <p>{@link #REG}(등록교육)는 위촉 요건, {@link #CONTINUING}(보수교육)은 주기적 이수기한(NEXT_DUE_DT)을 낳아 모집자격 게이트가
 * 된다(설계서 5.4). 나머지는 종목·컴플라이언스 부가 교육이다.
 */
public enum EduType {
  /** 등록교육 — 위촉 요건 */
  REG,
  /** 보수교육 — 주기적, NEXT_DUE_DT 게이트 */
  CONTINUING,
  /** 변액자격교육 */
  VARIABLE,
  /** 완전판매교육 */
  COMPLIANCE
}
