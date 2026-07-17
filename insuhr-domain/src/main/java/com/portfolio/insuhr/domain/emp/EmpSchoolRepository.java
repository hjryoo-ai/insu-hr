package com.portfolio.insuhr.domain.emp;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 인사기록카드 (설계서 6.5). 상태머신도 규칙도 없는 단순 이력이라 기본 CRUD + 직원별 목록이면 충분하다. */
public interface EmpSchoolRepository extends JpaRepository<EmpSchool, Long> {

  List<EmpSchool> findByEmpIdOrderByGradDtDesc(Long empId);
}
