package com.portfolio.insuhr.common.response;

import com.portfolio.insuhr.common.error.ErrorCode;
import com.portfolio.insuhr.common.error.ErrorDetail;
import java.util.List;

/**
 * 표준 응답 envelope (설계서 7.1).
 *
 * <pre>
 * { "success": true,  "data": {...}, "error": null,    "traceId": "..." }
 * { "success": false, "data": null,  "error": {...},   "traceId": "..." }
 * </pre>
 *
 * <p>성공/실패 어느 쪽이든 네 필드가 모두 나간다(null 포함). 수신측이 필드 존재를 가정할 수 있도록 생략하지 않는다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error, String traceId) {

  public static <T> ApiResponse<T> ok(T data, String traceId) {
    return new ApiResponse<>(true, data, null, traceId);
  }

  public static <T> ApiResponse<T> fail(ErrorBody error, String traceId) {
    return new ApiResponse<>(false, null, error, traceId);
  }

  public static <T> ApiResponse<T> fail(ErrorCode code, String message, String traceId) {
    return fail(new ErrorBody(code.code(), message, List.of()), traceId);
  }

  public static <T> ApiResponse<T> fail(
      ErrorCode code, String message, List<ErrorDetail> details, String traceId) {
    return fail(new ErrorBody(code.code(), message, details), traceId);
  }

  /** 실패 응답의 error 본문. */
  public record ErrorBody(String code, String message, List<ErrorDetail> details) {
    public ErrorBody {
      details = details == null ? List.of() : List.copyOf(details);
    }
  }
}
