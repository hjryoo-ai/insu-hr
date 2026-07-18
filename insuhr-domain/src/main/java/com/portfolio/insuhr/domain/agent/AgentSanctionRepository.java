package com.portfolio.insuhr.domain.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSanctionRepository extends JpaRepository<AgentSanction, Long> {

  List<AgentSanction> findByAgentId(Long agentId);
}
