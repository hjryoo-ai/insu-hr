package com.portfolio.insuhr.domain.agent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, Long> {

  Optional<Agent> findByAgentCd(String agentCd);

  Optional<Agent> findByPersonId(Long personId);

  List<Agent> findByOrgId(Long orgId);

  /**
   * 조직에 소속된 현역 설계사가 있는가 (설계서 7.2 — 조직 폐지 시 409).
   *
   * <p>TB_EMP의 재직자 검사와 대칭이다(설계서 5.3 v1.5로 갚는 절반). 해촉(TERMINATED)은 세지 않는다 — 해촉 설계사의 소속은 이력적 사실이라 조직
   * 폐지를 막을 이유가 없다(막으면 사람이 나간 조직을 영원히 못 닫는다).
   */
  boolean existsByOrgIdAndAgentStatusCdNot(Long orgId, String excludedStatusCd);
}
