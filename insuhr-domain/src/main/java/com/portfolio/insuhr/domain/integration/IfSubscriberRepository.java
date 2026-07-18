package com.portfolio.insuhr.domain.integration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 구독 시스템 리포지토리 (설계서 6.5). */
public interface IfSubscriberRepository extends JpaRepository<IfSubscriber, Long> {

  Optional<IfSubscriber> findBySystemCd(String systemCd);

  List<IfSubscriber> findAllByOrderBySystemCdAsc();
}
