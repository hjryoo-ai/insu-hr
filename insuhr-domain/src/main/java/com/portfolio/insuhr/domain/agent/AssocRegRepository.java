package com.portfolio.insuhr.domain.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssocRegRepository extends JpaRepository<AssocReg, Long> {

  List<AssocReg> findByAgentId(Long agentId);
}
