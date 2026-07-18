package com.portfolio.insuhr.batch.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;

/**
 * 개별 아이템 실패는 스킵하고 로그를 남긴다 — 잡 전체는 완주한다 (설계서 8: 실패 시 skip 로그).
 *
 * <p>한 설계사/임직원의 처리 실패가 나머지 대상의 처리를 막지 않게 한다. 스킵 한도(step 설정)를 넘으면 그때 잡이 실패한다.
 */
public class LoggingSkipListener<T, S> implements SkipListener<T, S> {

  private static final Logger log = LoggerFactory.getLogger(LoggingSkipListener.class);

  @Override
  public void onSkipInRead(Throwable t) {
    log.warn("배치 읽기 스킵: {}", t.toString());
  }

  @Override
  public void onSkipInProcess(T item, Throwable t) {
    log.warn("배치 처리 스킵 item={}: {}", item, t.toString());
  }

  @Override
  public void onSkipInWrite(S item, Throwable t) {
    log.warn("배치 쓰기 스킵 item={}: {}", item, t.toString());
  }
}
