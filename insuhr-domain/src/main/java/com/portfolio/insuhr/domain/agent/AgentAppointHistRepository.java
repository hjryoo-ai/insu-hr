package com.portfolio.insuhr.domain.agent;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentAppointHistRepository extends JpaRepository<AgentAppointHist, Long> {

  /** 이 설계사의 전이 이력을 시간순(기록순)으로. */
  List<AgentAppointHist> findByAgentIdOrderByIdAsc(Long agentId);

  /**
   * 가장 최근 재위촉(TERMINATED→CANDIDATE) 전이일 (설계서 5.4 v1.6 재위촉 요건 신선도).
   *
   * <p>{@code REG_EDU_REUSE_ON_REAPPOINT='N'}일 때 "이 날짜 이후 이수한 등록교육만 유효"의 기준이 된다. 재위촉 이력이 없으면 empty.
   */
  @Query(
      "select max(h.eventDt) from AgentAppointHist h "
          + "where h.agentId = :agentId and h.fromStatusCd = 'TERMINATED' and h.toStatusCd = 'CANDIDATE'")
  Optional<LocalDate> findLatestReappointDate(@Param("agentId") Long agentId);
}
