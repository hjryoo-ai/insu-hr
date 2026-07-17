package com.portfolio.insuhr.api.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 traceId를 MDC에 심는다 (설계서 10.4).
 *
 * <p>표준 응답 envelope의 traceId와 로그의 traceId가 같은 값이어야 장애 시 응답 하나로 로그를 추적할 수 있다. 호출측이 헤더로 넘긴 값이 있으면 그대로
 * 이어받아 시스템 간 추적이 끊기지 않게 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  public static final String TRACE_ID_HEADER = "X-Trace-Id";
  public static final String TRACE_ID_MDC_KEY = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String traceId = request.getHeader(TRACE_ID_HEADER);
    if (!StringUtils.hasText(traceId)) {
      traceId = UUID.randomUUID().toString().replace("-", "");
    }

    MDC.put(TRACE_ID_MDC_KEY, traceId);
    response.setHeader(TRACE_ID_HEADER, traceId);
    try {
      chain.doFilter(request, response);
    } finally {
      // 스레드 풀 재사용 환경에서 MDC가 다음 요청으로 새는 것을 막는다.
      MDC.remove(TRACE_ID_MDC_KEY);
    }
  }
}
