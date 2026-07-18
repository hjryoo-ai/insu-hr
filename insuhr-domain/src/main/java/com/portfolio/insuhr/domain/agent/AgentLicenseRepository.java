package com.portfolio.insuhr.domain.agent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentLicenseRepository extends JpaRepository<AgentLicense, Long> {

  List<AgentLicense> findByAgentId(Long agentId);

  Optional<AgentLicense> findByAgentIdAndLicenseTypeCd(Long agentId, String licenseTypeCd);
}
