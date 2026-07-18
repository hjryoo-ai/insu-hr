package com.portfolio.insuhr.domain.dq;

/**
 * 정합성 점검 룰 한 개 (설계서 8 v2.0).
 *
 * <p>{@code licenseValidityJob}·{@code dataQualityJob}·{@code outboxDlqSweepJob}이 공유하는 <b>공통
 * 골격</b>의 단위다 — 각 잡은 자신의 룰 목록만 다르게 들고, 실행(적재)은 {@link DqFindingDao#runRule}이 한다. 이렇게 잡을 합치지 않고도 "룰
 * 평가 → FINDING 적재" 골격을 공유한다(설계서 8 v2.0 경계 결정).
 *
 * @param ruleCd 룰 코드 — {@code TB_DQ_FINDING.RULE_CD}이자 dedup 키의 일부
 * @param targetTypeCd 대상 종류 (AGENT/EMP/OUTBOX/DELIVERY)
 * @param targetSelectSql {@code TARGET_ID}(숫자)와 {@code DETAIL}(JSON 문자열) 두 컬럼을 <b>대상당 한 행</b>으로 내는
 *     SELECT. 대상 중복 행이 있으면 한 문장 내 UQ 충돌이 나므로 반드시 {@code GROUP BY}/{@code DISTINCT}로 유일화한다. 잡 코드 상수라
 *     인젝션 위험 없음. {@code :foundDt} 바인드를 참조할 수 있다.
 */
public record DqRule(String ruleCd, String targetTypeCd, String targetSelectSql) {}
