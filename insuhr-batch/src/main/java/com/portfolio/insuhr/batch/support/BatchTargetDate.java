package com.portfolio.insuhr.batch.support;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.util.StringUtils;

/**
 * 잡 파라미터 {@code targetDate}(비옵션 인자, {@code yyyy-MM-dd})를 업무 기준일로 해석한다 (설계서 8 v2.0).
 *
 * <p>배치 잡은 시스템 날짜를 직접 읽지 않고 {@code targetDate}를 {@code asOf}로 도메인에 넘긴다 — 6.2 앵커 Clock 규약을 배치까지
 * 관통시키고, "같은 {@code targetDate} 재실행 = 같은 결과"라는 멱등성을 성립시킨다. 이 클래스가 규약의 <b>유일한</b> 시스템-날짜 접점이며,
 * {@code targetDate}가 없을 때만 주입 Clock의 오늘(KST)로 떨어진다.
 *
 * <p><b>파라미터는 {@link StepExecution}에서 읽는다(3.0 v2.0 배치 주의).</b> Batch 6에서
 * {@code @Value("#{jobParameters['targetDate']}")} 지연바인딩이 {@code null}로 떨어진다({@code
 * #{stepExecution}} 주입은 정상). 그래서 {@code @Value("#{stepExecution}")}로 받은 뒤 {@code
 * stepExecution.getJobParameters().getString(...)}로 읽는다.
 */
public final class BatchTargetDate {

  private BatchTargetDate() {}

  public static LocalDate resolve(StepExecution stepExecution, Clock clock) {
    String raw = stepExecution.getJobParameters().getString("targetDate");
    if (StringUtils.hasText(raw)) {
      return LocalDate.parse(raw.trim());
    }
    return LocalDate.now(clock);
  }
}
