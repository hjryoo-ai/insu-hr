package com.portfolio.insuhr.api.security;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.error.CommonErrorCode;
import com.portfolio.insuhr.common.error.ErrorCode;
import com.portfolio.insuhr.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증(401)·인가(403) 거부를 표준 응답 envelope으로 내보낸다 (설계서 7.1).
 *
 * <p>Security의 기본 거부 응답은 Spring의 ProblemDetail/빈 본문이라 우리 envelope과 형식이 다르다. 클라이언트가 응답 형식을 두 벌 다뤄야
 * 하는 상황을 만들지 않으려면 여기서 통일해야 한다. 이 거부들은 컨트롤러에 도달하기 전 필터 체인에서 나므로 {@code @RestControllerAdvice}가 잡지
 * 못한다.
 *
 * <p>직렬화에 Jackson 3(`tools.jackson`)을 쓴다 — Boot 4의 기본이며, `com.fasterxml`(2.x)을 쓰면 설정이 어긋난다(설계서
 * 3.0).
 */
@Component
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public SecurityErrorResponder(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** 인증 실패 — 토큰이 없거나 유효하지 않다. */
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
      throws IOException {
    write(response, CommonErrorCode.UNAUTHORIZED);
  }

  /** 인가 실패 — 인증은 됐지만 권한이 없다. */
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
      throws IOException {
    write(response, CommonErrorCode.FORBIDDEN);
  }

  private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setStatus(errorCode.httpStatus());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    ApiResponse<Void> body =
        ApiResponse.fail(errorCode, errorCode.defaultMessage(), TraceIdProvider.current());
    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
