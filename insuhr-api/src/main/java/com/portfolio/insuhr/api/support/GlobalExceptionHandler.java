package com.portfolio.insuhr.api.support;

import com.portfolio.insuhr.common.error.CommonErrorCode;
import com.portfolio.insuhr.common.error.ErrorDetail;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.common.response.ApiResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 예외 → 표준 응답 envelope 변환 (설계서 7.1).
 *
 * <p>common 모듈은 프레임워크 무관이라 HTTP 상태를 int로만 들고 있다. Spring 타입으로의 변환은 여기서 한 번만 한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 업무 규칙 위반. 장애가 아니므로 WARN + 스택트레이스 없이 기록한다. */
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
    log.warn("business rule violation: code={} message={}", e.errorCode().code(), e.getMessage());
    return ResponseEntity.status(e.errorCode().httpStatus())
        .body(
            ApiResponse.fail(
                e.errorCode(), e.getMessage(), e.details(), TraceIdProvider.current()));
  }

  /**
   * @Valid 실패 → 필드별 사유를 details 배열로 펼친다.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
    List<ErrorDetail> details =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> ErrorDetail.of(fe.getField(), fe.getCode(), fe.getDefaultMessage()))
            .toList();

    return ResponseEntity.status(CommonErrorCode.INVALID_REQUEST.httpStatus())
        .body(
            ApiResponse.fail(
                CommonErrorCode.INVALID_REQUEST,
                CommonErrorCode.INVALID_REQUEST.defaultMessage(),
                details,
                TraceIdProvider.current()));
  }

  /**
   * 인가 실패(403).
   *
   * <p>이 핸들러가 필요한 이유: {@code @PreAuthorize}는 컨트롤러 메서드 호출 시점에 AccessDeniedException을 던지므로, Security의
   * AccessDeniedHandler(필터 체인)가 아니라 이 advice가 먼저 잡는다. 명시적으로 다루지 않으면 아래 Exception 핸들러가 삼켜 403이어야 할
   * 응답이 500으로 나간다.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
    log.warn("access denied: {}", e.getMessage());
    return ResponseEntity.status(CommonErrorCode.FORBIDDEN.httpStatus())
        .body(
            ApiResponse.fail(
                CommonErrorCode.FORBIDDEN,
                CommonErrorCode.FORBIDDEN.defaultMessage(),
                TraceIdProvider.current()));
  }

  /**
   * 낙관적 잠금 충돌 → 409 (설계서 5.3 v1.5).
   *
   * <p>두 담당자가 같은 설계사에게 동시에 전이를 걸면 진 쪽의 {@code @Version}이 어긋나 여기로 온다. 상태머신 서비스는 검사+쓰기가 원자적이라고 가정하는데,
   * 그 원자성을 실제로 보장하는 것이 이 잠금이다. 명시적으로 다루지 않으면 아래 Exception 핸들러가 500으로 삼킨다.
   */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
      OptimisticLockingFailureException e) {
    log.warn("optimistic lock conflict: {}", e.getMessage());
    return ResponseEntity.status(CommonErrorCode.CONFLICT.httpStatus())
        .body(
            ApiResponse.fail(
                CommonErrorCode.CONFLICT,
                "다른 사용자가 먼저 상태를 변경했습니다. 다시 시도하십시오.",
                TraceIdProvider.current()));
  }

  /** 예상 못 한 예외. 내부 메시지를 클라이언트에 흘리지 않고 traceId로만 추적하게 한다. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
    log.error("unhandled exception", e);
    return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.httpStatus())
        .body(
            ApiResponse.fail(
                CommonErrorCode.INTERNAL_ERROR,
                CommonErrorCode.INTERNAL_ERROR.defaultMessage(),
                TraceIdProvider.current()));
  }
}
