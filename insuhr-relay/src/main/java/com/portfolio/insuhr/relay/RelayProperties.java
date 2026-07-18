package com.portfolio.insuhr.relay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 릴레이 인프라 설정 (설계서 9.2).
 *
 * <p>여기 값들은 <b>운영 파라미터</b>다 — 재시도 <b>한도</b>는 법·정책성이라 {@code TB_POLICY_CONFIG}의 {@code
 * OUTBOX_MAX_RETRY}에서 읽고(하드코딩 금지), 여기서는 백오프 기저·타임아웃·배치크기만 잡는다.
 */
@ConfigurationProperties(prefix = "insuhr.relay")
public class RelayProperties {

  /** 한 번의 폴에서 처리할 최대 건수(팬아웃·전달 각각). */
  private int batchSize = 100;

  /** 지수 백오프 기저(초). {@code base * 2^(재시도횟수-1)}. 테스트는 0으로 두어 즉시 재시도. */
  private long retryBaseSeconds = 30;

  /** 웹훅 연결 타임아웃(ms). 죽은 엔드포인트가 폴러를 오래 붙잡지 않게 짧게. */
  private long connectTimeoutMs = 2000;

  /** 웹훅 읽기 타임아웃(ms). */
  private long readTimeoutMs = 3000;

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  public long getRetryBaseSeconds() {
    return retryBaseSeconds;
  }

  public void setRetryBaseSeconds(long retryBaseSeconds) {
    this.retryBaseSeconds = retryBaseSeconds;
  }

  public long getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public void setConnectTimeoutMs(long connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public long getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public void setReadTimeoutMs(long readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }
}
