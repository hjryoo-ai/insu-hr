package com.portfolio.insuhr.relay;

/**
 * 웹훅 1회 전송 결과 (설계서 9.2).
 *
 * @param success 2xx 응답이면 참
 * @param httpStatus 응답 HTTP 상태(네트워크 실패면 null)
 * @param body 응답 본문(전송이력 기록용)
 * @param error 실패 사유(성공이면 null)
 */
public record PublishResult(boolean success, Integer httpStatus, String body, String error) {

  public static PublishResult success(Integer httpStatus, String body) {
    return new PublishResult(true, httpStatus, body, null);
  }

  public static PublishResult failure(Integer httpStatus, String error) {
    return new PublishResult(false, httpStatus, null, error);
  }
}
