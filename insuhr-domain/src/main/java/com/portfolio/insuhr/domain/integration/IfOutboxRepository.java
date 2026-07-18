package com.portfolio.insuhr.domain.integration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IfOutboxRepository extends JpaRepository<IfOutbox, Long> {

  Optional<IfOutbox> findByEventUuid(String eventUuid);

  List<IfOutbox> findByAggTypeAndAggIdOrderByIdAsc(String aggType, Long aggId);

  List<IfOutbox> findByStatusCdOrderByIdAsc(String statusCd);
}
