package com.portfolio.insuhr.domain.leave;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

  List<LeaveRequest> findByEmpIdOrderByStartDtDesc(Long empId);
}
