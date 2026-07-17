package com.portfolio.insuhr.domain.audit;

/** 개인정보 접근 유형 (공통코드 ACCESS_TYPE — 설계서 10.2). */
public enum PrivacyAccessType {
  /** 마스킹 상태의 목록/상세 조회 */
  VIEW,
  /** 원문 복호화. 사유 입력 필수 */
  DECRYPT,
  /** 대량 내보내기 (파일/스냅샷 API). 시스템 계정 한정 */
  EXPORT
}
