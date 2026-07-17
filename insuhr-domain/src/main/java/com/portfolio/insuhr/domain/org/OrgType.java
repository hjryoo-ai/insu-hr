package com.portfolio.insuhr.domain.org;

/** 조직 유형 (공통코드 ORG_TYPE). 본사 조직과 영업 조직이 한 계층에 공존한다 (설계서 1.3). */
public enum OrgType {
  /** 본사부서 */
  HQ_DEPT,
  /** 지역단 */
  REGION,
  /** 지점 */
  BRANCH,
  /** 영업소 */
  OFFICE
}
