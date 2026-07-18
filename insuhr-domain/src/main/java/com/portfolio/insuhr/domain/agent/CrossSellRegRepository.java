package com.portfolio.insuhr.domain.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrossSellRegRepository extends JpaRepository<CrossSellReg, Long> {

  List<CrossSellReg> findByAgentId(Long agentId);
}
