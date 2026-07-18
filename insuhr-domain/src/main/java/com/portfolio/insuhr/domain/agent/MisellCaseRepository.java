package com.portfolio.insuhr.domain.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MisellCaseRepository extends JpaRepository<MisellCase, Long> {

  List<MisellCase> findByAgentId(Long agentId);
}
