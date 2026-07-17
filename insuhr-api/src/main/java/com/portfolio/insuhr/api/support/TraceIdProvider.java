package com.portfolio.insuhr.api.support;

import org.slf4j.MDC;

/** 현재 요청의 traceId 조회. */
public final class TraceIdProvider {

  private TraceIdProvider() {}

  /** MDC에 심긴 traceId. 필터를 타지 않은 경로(배치 등)에서는 빈 문자열. */
  public static String current() {
    String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
    return traceId == null ? "" : traceId;
  }
}
