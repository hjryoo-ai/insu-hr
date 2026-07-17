package com.portfolio.insuhr.domain.leave;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveGrantRepository extends JpaRepository<LeaveGrant, Long> {

  List<LeaveGrant> findByEmpIdOrderByYearNoDesc(Long empId);

  Optional<LeaveGrant> findByEmpIdAndYearNo(Long empId, int yearNo);

  boolean existsByEmpIdAndYearNo(Long empId, int yearNo);
}
