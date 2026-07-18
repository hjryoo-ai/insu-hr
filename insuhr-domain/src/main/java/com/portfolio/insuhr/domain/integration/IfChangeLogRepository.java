package com.portfolio.insuhr.domain.integration;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IfChangeLogRepository extends JpaRepository<IfChangeLog, Long> {

  List<IfChangeLog> findByAggTypeOrderBySeqNoAsc(String aggType);
}
