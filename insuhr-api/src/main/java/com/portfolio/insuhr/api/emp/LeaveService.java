package com.portfolio.insuhr.api.emp;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.emp.EmpErrorCode;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.leave.LeaveErrorCode;
import com.portfolio.insuhr.domain.leave.LeaveGrant;
import com.portfolio.insuhr.domain.leave.LeaveGrantRepository;
import com.portfolio.insuhr.domain.leave.LeaveRequest;
import com.portfolio.insuhr.domain.leave.LeaveRequestRepository;
import com.portfolio.insuhr.domain.leave.LeaveType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴가·연차 유스케이스 (설계서 7.2, 6.5).
 *
 * <p><b>연차 차감은 신청이 아니라 승인 시점</b>이다. 신청만으로 잔여가 줄면 반려·취소 시 복원이 지저분해지고, 무엇보다 "신청했으나 미승인" 상태가 잔여를 잠그면 안
 * 된다. 그래서 {@link #requestLeave}는 잔여를 건드리지 않고 {@link #approve}에서 {@link LeaveGrant#consume}를 부른다.
 *
 * <p>연차 부여 자체(연 단위 부여)는 Phase 7의 {@code annualLeaveGrantJob}이 하지만, 부여 API도 열어 둔다 — 중도 입사자 수동 부여 등.
 * 같은 직원·연도 중복 부여는 {@code UQ_LEAVE_GRANT}가 막는다(배치 재실행 멱등성의 방어선).
 */
@Service
@Transactional
public class LeaveService {

  private final EmpRepository empRepository;
  private final LeaveGrantRepository grantRepository;
  private final LeaveRequestRepository requestRepository;

  public LeaveService(
      EmpRepository empRepository,
      LeaveGrantRepository grantRepository,
      LeaveRequestRepository requestRepository) {
    this.empRepository = empRepository;
    this.grantRepository = grantRepository;
    this.requestRepository = requestRepository;
  }

  /** 연차 부여. 같은 연도 중복 부여는 유니크 제약이 막는다. */
  public Long grant(Long empId, int yearNo, BigDecimal grantDays, LocalDate expireDt) {
    requireEmp(empId);
    if (grantRepository.existsByEmpIdAndYearNo(empId, yearNo)) {
      throw new BusinessException(
          LeaveErrorCode.ALREADY_GRANTED, "이미 " + yearNo + "년 연차가 부여되었습니다. empId=" + empId);
    }
    return grantRepository.save(LeaveGrant.grant(empId, yearNo, grantDays, expireDt)).getId();
  }

  /** 휴가 신청. 잔여는 아직 건드리지 않는다 — 차감은 승인 시점(설계서 참조). */
  public Long requestLeave(
      Long empId,
      LeaveType type,
      LocalDate startDt,
      LocalDate endDt,
      BigDecimal days,
      String rsnTxt) {
    requireEmp(empId);
    return requestRepository
        .save(LeaveRequest.request(empId, type, startDt, endDt, days, rsnTxt))
        .getId();
  }

  /**
   * 휴가 승인.
   *
   * <p>연차 유형이면 이 트랜잭션에서 잔여를 차감한다. 잔여 부족이면 {@link LeaveGrant#consume}가 던지고 승인 자체가 롤백된다 — 승인은 됐는데 차감이
   * 안 되는 어긋남이 생기지 않는다.
   */
  public void approve(Long leaveReqId, Long approverEmpId) {
    LeaveRequest request = requireRequest(leaveReqId);
    if (request.deductsAnnual()) {
      grantOf(request.getEmpId(), request.getStartDt()).consume(request.getDays());
    }
    request.approve(approverEmpId);
  }

  public void reject(Long leaveReqId, Long approverEmpId) {
    requireRequest(leaveReqId).reject(approverEmpId);
  }

  /**
   * 휴가 취소.
   *
   * <p>승인됐던 연차 신청을 취소하면 차감을 복원한다. 신청·미승인 건은 차감이 없었으니 복원도 없다.
   */
  public void cancel(Long leaveReqId) {
    LeaveRequest request = requireRequest(leaveReqId);
    boolean wasApprovedAnnual = request.isApproved() && request.deductsAnnual();
    request.cancel();
    if (wasApprovedAnnual) {
      grantOf(request.getEmpId(), request.getStartDt()).restore(request.getDays());
    }
  }

  @Transactional(readOnly = true)
  public List<LeaveRequest> requests(Long empId) {
    return requestRepository.findByEmpIdOrderByStartDtDesc(empId);
  }

  @Transactional(readOnly = true)
  public List<LeaveGrant> grants(Long empId) {
    return grantRepository.findByEmpIdOrderByYearNoDesc(empId);
  }

  /** 휴가 시작일 연도의 연차 부여를 찾는다. 차감 대상이 되는 연차가 그 해에 없으면 오류. */
  private LeaveGrant grantOf(Long empId, LocalDate startDt) {
    int yearNo = startDt.getYear();
    return grantRepository
        .findByEmpIdAndYearNo(empId, yearNo)
        .orElseThrow(
            () ->
                new BusinessException(
                    LeaveErrorCode.GRANT_NOT_FOUND, yearNo + "년 연차 부여 내역이 없습니다. empId=" + empId));
  }

  private void requireEmp(Long empId) {
    if (!empRepository.existsById(empId)) {
      throw new BusinessException(EmpErrorCode.NOT_FOUND, "임직원을 찾을 수 없습니다: " + empId);
    }
  }

  private LeaveRequest requireRequest(Long leaveReqId) {
    return requestRepository
        .findById(leaveReqId)
        .orElseThrow(
            () ->
                new BusinessException(
                    LeaveErrorCode.REQUEST_NOT_FOUND, "휴가 신청을 찾을 수 없습니다: " + leaveReqId));
  }
}
