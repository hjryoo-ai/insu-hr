package com.portfolio.insuhr.domain.agent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAppointHistRepository extends JpaRepository<AgentAppointHist, Long> {

  /** 이 설계사의 전이 이력을 시간순(기록순)으로. */
  List<AgentAppointHist> findByAgentIdOrderByIdAsc(Long agentId);
}
