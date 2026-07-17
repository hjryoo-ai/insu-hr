package com.portfolio.insuhr.common.error;

/**
 * 에러코드 계약 (설계서 7.1).
 *
 * <p>코드 형식: {도메인 3자}-{HTTP류 2자}{일련 2자} — 예: AGT-4221 (설계사 도메인, 422류, 일련 21). 도메인 3자는
 * COM/ORG/PER/EMP/AGT/LIC/EDU/GRT/SNC/IFC/AUT.
 *
 * <p>도메인별로 이 인터페이스를 구현한 enum을 각 Phase에서 추가한다. common 모듈은 프레임워크 무관이므로 HTTP 상태를 Spring 타입이 아닌 int 로
 * 들고 있고, 변환은 api 모듈이 담당한다.
 */
public interface ErrorCode {

  /** 클라이언트에 노출되는 에러코드 문자열. 예: {@code AGT-4221} */
  String code();

  /** HTTP 응답 상태. */
  int httpStatus();

  /** 기본 메시지. 호출부가 상황별 메시지로 덮어쓸 수 있다. */
  String defaultMessage();
}
