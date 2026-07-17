package com.portfolio.insuhr.domain.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyAccessLogRepository extends JpaRepository<PrivacyAccessLog, Long> {

  List<PrivacyAccessLog> findByTargetPersonIdOrderByAccessAtDesc(Long targetPersonId);
}
