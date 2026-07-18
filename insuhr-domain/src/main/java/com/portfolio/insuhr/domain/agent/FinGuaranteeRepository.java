package com.portfolio.insuhr.domain.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinGuaranteeRepository extends JpaRepository<FinGuarantee, Long> {

  List<FinGuarantee> findByAgentId(Long agentId);
}
