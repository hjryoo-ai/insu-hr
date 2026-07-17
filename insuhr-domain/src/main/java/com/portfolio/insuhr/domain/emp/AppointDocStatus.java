package com.portfolio.insuhr.domain.emp;

/** 발령 문서 상태 (설계서 5.5). 기안 → 확정 → (필요시) 취소. */
public enum AppointDocStatus {
  DRAFT,
  CONFIRMED,
  CANCELED
}
