package com.portfolio.insuhr.domain.agent;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EduPolicyRepository extends JpaRepository<EduPolicy, Long> {

  Optional<EduPolicy> findFirstByEduTypeCdOrderByValidFromDtDesc(String eduTypeCd);
}
