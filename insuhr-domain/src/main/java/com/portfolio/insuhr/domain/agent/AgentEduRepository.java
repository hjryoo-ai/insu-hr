package com.portfolio.insuhr.domain.agent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEduRepository extends JpaRepository<AgentEdu, Long> {

  List<AgentEdu> findByAgentId(Long agentId);

  /** 보수교육 게이트: 이 설계사의 CONTINUING 이수 중 NEXT_DUE_DT가 가장 늦은 것(최신 기한). */
  Optional<AgentEdu> findFirstByAgentIdAndEduTypeCdOrderByNextDueDtDesc(
      Long agentId, String eduTypeCd);

  /** 위촉 요건: 등록교육(REG) 이수 존재 여부. */
  boolean existsByAgentIdAndEduTypeCd(Long agentId, String eduTypeCd);

  /** 재위촉 신선도(REUSE='N'): 특정일 이후 이수한 REG 교육 존재 여부. */
  boolean existsByAgentIdAndEduTypeCdAndCompleteDtGreaterThanEqual(
      Long agentId, String eduTypeCd, java.time.LocalDate onOrAfter);
}
