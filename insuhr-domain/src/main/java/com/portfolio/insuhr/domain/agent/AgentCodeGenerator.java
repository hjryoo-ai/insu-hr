package com.portfolio.insuhr.domain.agent;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 설계사코드 채번 (설계서 6.4 v1.5).
 *
 * <p>{@code SEQ_AGENT_CD}에서 원자적으로 다음 값을 받아 {@code A} + 8자리 0패딩으로 만든다. 사번({@code EmpNoGenerator})과
 * 같은 규칙·같은 이유다 — MAX+1은 동시 위촉에서 {@code UQ_AGENT_CD}를 깨고, 시퀀스는 그 창이 없다. 연도를 넣지 않는 것도 사번과 같다(재위촉·소급에서
 * 코드의 연도와 실제 위촉일이 어긋난다).
 */
@Component
public class AgentCodeGenerator {

  private final JdbcClient jdbcClient;

  public AgentCodeGenerator(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public String next() {
    long seq = jdbcClient.sql("SELECT SEQ_AGENT_CD.NEXTVAL FROM DUAL").query(Long.class).single();
    return String.format("A%08d", seq);
  }
}
