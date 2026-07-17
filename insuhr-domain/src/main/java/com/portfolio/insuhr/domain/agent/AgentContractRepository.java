package com.portfolio.insuhr.domain.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentContractRepository extends JpaRepository<AgentContract, Long> {

  List<AgentContract> findByAgentIdOrderByValidFromDtDesc(Long agentId);
}
