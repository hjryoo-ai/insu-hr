package com.portfolio.insuhr.domain.leave;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 휴가 신청 (설계서 6.5, 7.2).
 *
 * <p>상태 전이는 신청 → 승인/반려, 그리고 신청·승인 → 취소. 연차 차감은 <b>승인 시점</b>에 일어나므로(신청만으로 잔여가 줄지 않는다) 차감/복원 자체는
 * 애플리케이션 서비스가 {@link LeaveGrant}와 함께 다룬다 — 이 엔티티는 상태만 지킨다.
 */
@Entity
@Table(name = "TB_LEAVE_REQ")
public class LeaveRequest extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "LEAVE_REQ_ID")
  private Long id;

  @Column(name = "EMP_ID", nullable = false)
  private Long empId;

  @Column(name = "LEAVE_TYPE_CD", nullable = false, length = 30)
  private String leaveTypeCd;

  @Column(name = "START_DT", nullable = false)
  private LocalDate startDt;

  @Column(name = "END_DT", nullable = false)
  private LocalDate endDt;

  @Column(name = "DAYS", nullable = false, precision = 4, scale = 1)
  private BigDecimal days;

  @Column(name = "STATUS_CD", nullable = false, length = 30)
  private String statusCd;

  @Column(name = "APPROVER_EMP_ID")
  private Long approverEmpId;

  @Column(name = "RSN_TXT", length = 400)
  private String rsnTxt;

  protected LeaveRequest() {}

  private LeaveRequest(
      Long empId,
      LeaveType type,
      LocalDate startDt,
      LocalDate endDt,
      BigDecimal days,
      String rsnTxt) {
    this.empId = empId;
    this.leaveTypeCd = type.name();
    this.startDt = startDt;
    this.endDt = endDt;
    this.days = days;
    this.statusCd = LeaveStatus.REQUESTED.name();
    this.rsnTxt = rsnTxt;
  }

  public static LeaveRequest request(
      Long empId,
      LeaveType type,
      LocalDate startDt,
      LocalDate endDt,
      BigDecimal days,
      String rsnTxt) {
    if (endDt.isBefore(startDt)) {
      throw new BusinessException(LeaveErrorCode.INVALID_PERIOD);
    }
    if (days.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BusinessException(LeaveErrorCode.INVALID_DAYS);
    }
    return new LeaveRequest(empId, type, startDt, endDt, days, rsnTxt);
  }

  public void approve(Long approverEmpId) {
    requirePending();
    this.statusCd = LeaveStatus.APPROVED.name();
    this.approverEmpId = approverEmpId;
  }

  public void reject(Long approverEmpId) {
    requirePending();
    this.statusCd = LeaveStatus.REJECTED.name();
    this.approverEmpId = approverEmpId;
  }

  /** 취소. 승인된 건도 취소할 수 있어야 차감 복원의 트리거가 된다 — 반려/취소된 건만 막는다. */
  public void cancel() {
    if (isRejected() || isCanceled()) {
      throw new BusinessException(
          LeaveErrorCode.NOT_PENDING, "이미 종료된 휴가 신청은 취소할 수 없습니다. 상태=" + statusCd);
    }
    this.statusCd = LeaveStatus.CANCELED.name();
  }

  private void requirePending() {
    if (!isPending()) {
      throw new BusinessException(
          LeaveErrorCode.NOT_PENDING, "신청 상태의 휴가만 처리할 수 있습니다. 현재 상태=" + statusCd);
    }
  }

  public boolean isPending() {
    return LeaveStatus.REQUESTED.name().equals(statusCd);
  }

  public boolean isApproved() {
    return LeaveStatus.APPROVED.name().equals(statusCd);
  }

  public boolean isRejected() {
    return LeaveStatus.REJECTED.name().equals(statusCd);
  }

  public boolean isCanceled() {
    return LeaveStatus.CANCELED.name().equals(statusCd);
  }

  public LeaveType getType() {
    return LeaveType.valueOf(leaveTypeCd);
  }

  /** 연차에서 차감되는 신청인가. 승인/취소 시 {@link LeaveGrant} 갱신 여부를 정한다. */
  public boolean deductsAnnual() {
    return getType().deductsAnnual();
  }

  public Long getId() {
    return id;
  }

  public Long getEmpId() {
    return empId;
  }

  public LocalDate getStartDt() {
    return startDt;
  }

  public LocalDate getEndDt() {
    return endDt;
  }

  public BigDecimal getDays() {
    return days;
  }

  public LeaveStatus getStatus() {
    return LeaveStatus.valueOf(statusCd);
  }

  public Long getApproverEmpId() {
    return approverEmpId;
  }

  public String getRsnTxt() {
    return rsnTxt;
  }
}
