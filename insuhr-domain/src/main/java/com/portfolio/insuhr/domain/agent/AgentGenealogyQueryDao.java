package com.portfolio.insuhr.domain.agent;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 도입 계보 조회 (설계서 7.2 {@code GET /agents/{id}/genealogy}).
 *
 * <p>도입자({@code RECRUITER_AGENT_ID}) 자기참조를 타고 내려가는 Oracle 계층 질의({@code CONNECT BY})다. 조직
 * 트리(OrgQueryDao)와 같은 발상이되 대상이 조직이 아니라 설계사다.
 *
 * <p><b>{@code NOCYCLE}은 이중 방어다 (설계서 5.3 v1.5).</b> 도입자 지정 시점에 조상 체인을 검사해 순환을 막는 것이 1차 방어(A→B→A를
 * 애초에 못 만들게)이고, 그럼에도 데이터가 순환하면 이 쿼리가 {@code ORA-01436}(CONNECT BY loop)으로 죽는다. {@code NOCYCLE}은 그때
 * 죽는 대신 순환 지점을 끊고 결과를 돌려줘, 조회가 데이터 오염에 무릎 꿇지 않게 한다.
 */
@Repository
public class AgentGenealogyQueryDao {

  private final JdbcClient jdbcClient;

  public AgentGenealogyQueryDao(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /** 이 설계사를 루트로 한 도입 계보(자신 + 하위 도입자 트리). {@code depth} 1 = 루트. */
  public List<GenealogyRow> findGenealogy(Long rootAgentId) {
    return jdbcClient
        .sql(
            """
            SELECT AGENT_ID           AS agentId,
                   AGENT_CD           AS agentCd,
                   RECRUITER_AGENT_ID AS recruiterAgentId,
                   AGENT_STATUS_CD    AS statusCd,
                   ORG_ID             AS orgId,
                   LEVEL              AS depth
              FROM TB_AGENT
             START WITH AGENT_ID = :rootAgentId
             CONNECT BY NOCYCLE PRIOR AGENT_ID = RECRUITER_AGENT_ID
             ORDER SIBLINGS BY AGENT_CD
            """)
        .param("rootAgentId", rootAgentId)
        .query(GenealogyRow.class)
        .list();
  }

  /**
   * 이 설계사의 하위 계보 ID 목록 (자기 자신 포함).
   *
   * <p>도입자 지정 순환 방어에 쓴다 — X를 Y의 도입자로 지정하려는데 X가 Y의 하위 계보에 있으면 순환이므로 거부한다(조직 이관의 {@code
   * guardAgainstCycle}과 같은 발상).
   */
  public List<Long> findDescendantAgentIds(Long rootAgentId) {
    return jdbcClient
        .sql(
            """
            SELECT AGENT_ID
              FROM TB_AGENT
             START WITH AGENT_ID = :rootAgentId
             CONNECT BY NOCYCLE PRIOR AGENT_ID = RECRUITER_AGENT_ID
            """)
        .param("rootAgentId", rootAgentId)
        .query(Long.class)
        .list();
  }

  /**
   * 계보 평면 행.
   *
   * @param recruiterAgentId 도입자(부모). 루트면 트리 밖을 가리키거나 null
   * @param depth CONNECT BY LEVEL. 루트=1
   */
  public record GenealogyRow(
      Long agentId,
      String agentCd,
      Long recruiterAgentId,
      String statusCd,
      Long orgId,
      Integer depth) {}
}
