package com.portfolio.insuhr.batch.support;

import com.portfolio.insuhr.domain.dq.DqFindingDao;
import com.portfolio.insuhr.domain.dq.DqRule;
import java.time.LocalDate;
import java.util.List;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/**
 * 정합성 점검 잡의 <b>공통 골격</b> (설계서 8 v2.0).
 *
 * <p>{@code licenseValidityJob}·{@code dataQualityJob}·{@code outboxDlqSweepJob}은 <b>별도 잡으로
 * 유지하되</b>(각기 다른 시각·관점), "룰 평가 → FINDING 적재"라는 뼈대는 여기서 공유한다 — 각 잡은 자신의 {@link DqRule} 목록만 다르게 들고, 이
 * 태스클릿이 {@link DqFindingDao#runRule}로 한 룰씩 멱등 적재한다. 상태를 바꾸지 않고 관측만 하므로 이벤트 발행도 없다.
 *
 * <p>이 결정(합치지 않고 골격만 추출)의 근거는 §8에 남긴다 — 셋은 대상 도메인이 다르고(자격/조직·협회/연계) 스케줄도 달라, 룰셋을 한 잡에 뭉치면 실패 격리와
 * 스케줄 독립성을 잃는다.
 */
public final class DqSweepTasklet {

  private DqSweepTasklet() {}

  /** 룰 목록을 기준일에 멱등 적재하는 태스클릿을 만든다. */
  public static Tasklet of(DqFindingDao dqFindingDao, List<DqRule> rules, LocalDate foundDt) {
    return (contribution, chunkContext) -> {
      for (DqRule rule : rules) {
        dqFindingDao.runRule(rule, foundDt);
      }
      return RepeatStatus.FINISHED;
    };
  }
}
