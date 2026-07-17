package com.portfolio.insuhr.domain.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정책값 조회 (설계서 13.1 "법령 의존 수치는 TB_POLICY_CONFIG에서 읽는다. 하드코딩 금지").
 *
 * <p>같은 키를 유효기간으로 이력 관리하므로(6.6 유효기간형) 기준일자에 유효한 행 하나를 고른다. 법이 바뀌어 값이 달라져도 과거 판정을 그 시점 기준으로 재현할 수
 * 있어야 한다 — 그래서 asOf를 받는 조회를 기본형으로 둔다.
 *
 * <p><b>기본값을 두지 않는다.</b> 키가 없으면 예외를 던진다. 코드에 "없으면 24개월" 같은 폴백을 두는 순간 정책 테이블을 우회하는 하드코딩이 되고, 시드 누락을
 * 배포 후에야 알게 된다.
 */
@Service
public class PolicyConfigService {

  private final JdbcClient jdbcClient;

  public PolicyConfigService(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /** 오늘 기준 유효한 정책값(문자열). */
  @Transactional(readOnly = true)
  public String getString(PolicyKey key) {
    return getString(key, LocalDate.now());
  }

  /** 기준일자에 유효한 정책값(문자열). */
  @Transactional(readOnly = true)
  public String getString(PolicyKey key, LocalDate asOf) {
    return find(key, asOf)
        .orElseThrow(
            () -> new PolicyNotFoundException("정책값이 없습니다. key=" + key.name() + ", asOf=" + asOf));
  }

  @Transactional(readOnly = true)
  public int getInt(PolicyKey key) {
    return getInt(key, LocalDate.now());
  }

  @Transactional(readOnly = true)
  public int getInt(PolicyKey key, LocalDate asOf) {
    String raw = getString(key, asOf);
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new PolicyNotFoundException("정책값이 정수가 아닙니다. key=" + key.name() + ", value=" + raw, e);
    }
  }

  @Transactional(readOnly = true)
  public BigDecimal getDecimal(PolicyKey key) {
    return getDecimal(key, LocalDate.now());
  }

  @Transactional(readOnly = true)
  public BigDecimal getDecimal(PolicyKey key, LocalDate asOf) {
    String raw = getString(key, asOf);
    try {
      return new BigDecimal(raw.trim());
    } catch (NumberFormatException e) {
      throw new PolicyNotFoundException("정책값이 숫자가 아닙니다. key=" + key.name() + ", value=" + raw, e);
    }
  }

  private Optional<String> find(PolicyKey key, LocalDate asOf) {
    return jdbcClient
        .sql(
            """
            SELECT POLICY_VAL
              FROM TB_POLICY_CONFIG
             WHERE POLICY_KEY = :key
               AND :asOf BETWEEN VALID_FROM_DT AND VALID_TO_DT
             ORDER BY VALID_FROM_DT DESC
             FETCH FIRST 1 ROW ONLY
            """)
        .param("key", key.name())
        .param("asOf", java.sql.Date.valueOf(asOf))
        .query(String.class)
        .optional();
  }
}
