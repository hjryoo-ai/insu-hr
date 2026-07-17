package com.portfolio.insuhr.domain.org;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgRepository extends JpaRepository<Org, Long> {

  Optional<Org> findByOrgCd(String orgCd);

  boolean existsByOrgCd(String orgCd);

  List<Org> findByUpOrgId(Long upOrgId);

  /** 폐지되지 않은 하위 조직. 조직 폐지 가능 여부 판정에 쓴다 (설계서 7.2 — 하위조직 존재 시 409). */
  List<Org> findByUpOrgIdAndUseYnTrue(Long upOrgId);
}
