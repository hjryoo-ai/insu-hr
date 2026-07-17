package com.portfolio.insuhr.domain.emp;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 사번 채번 (설계서 6.4 v1.4).
 *
 * <p>{@code SEQ_EMP_NO}에서 원자적으로 다음 값을 받아 {@code E} + 8자리 0패딩으로 만든다. MAX+1이 아니라 시퀀스인 이유는 동시 입사에서
 * {@code UQ_EMP_NO}가 깨지지 않게 하기 위함이다 — 시퀀스는 트랜잭션 밖에서 증가해 두 요청이 같은 값을 받는 창이 없다.
 *
 * <p>롤백 시 번호 갭이 생기지만 사번이 무의미 번호라 문제되지 않는다(갭을 메우려는 순간 MAX+1 문제로 돌아간다).
 */
@Component
public class EmpNoGenerator {

  private final JdbcClient jdbcClient;

  public EmpNoGenerator(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String next() {
    long seq = jdbcClient.sql("SELECT SEQ_EMP_NO.NEXTVAL FROM DUAL").query(Long.class).single();
    return String.format("E%08d", seq);
  }
}
