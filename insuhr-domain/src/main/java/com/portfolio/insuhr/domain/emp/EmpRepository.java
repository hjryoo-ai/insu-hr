package com.portfolio.insuhr.domain.emp;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmpRepository extends JpaRepository<Emp, Long> {

  Optional<Emp> findByEmpNo(String empNo);

  Optional<Emp> findByPersonId(Long personId);

  List<Emp> findByOrgId(Long orgId);

  /**
   * 조직에 소속된 재직자가 있는가 (설계서 7.2 — 조직 폐지 시 409).
   *
   * <p>퇴직자는 세지 않는다. 퇴직자의 소속은 "마지막 소속"이라는 이력적 사실이라 조직 폐지를 막을 이유가 없다 — 막으면 사람이 나간 조직을 영원히 못 닫는다.
   */
  boolean existsByOrgIdAndEmpStatusCdNot(Long orgId, String excludedStatusCd);
}
