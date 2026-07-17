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
 * 연차 부여 (설계서 6.5).
 *
 * <p>잔여일수는 컬럼이 아니라 {@code grantDays - usedDays} 파생값이다 — 잔여를 따로 저장하면 부여·사용과 어긋날 수 있는 세 번째 진실이 된다.
 *
 * <p>사용일수 증가는 {@link #consume}를 통해서만 일어나고, 부여일수를 넘기지 못한다(DB의 {@code CK_LEAVE_GRANT_DAYS}와 같은 규칙을
 * 도메인에서 먼저 막는다).
 */
@Entity
@Table(name = "TB_LEAVE_GRANT")
public class LeaveGrant extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "GRANT_ID")
  private Long id;

  @Column(name = "EMP_ID", nullable = false)
  private Long empId;

  @Column(name = "YEAR_NO", nullable = false)
  private int yearNo;

  @Column(name = "GRANT_DAYS", nullable = false, precision = 4, scale = 1)
  private BigDecimal grantDays;

  @Column(name = "USED_DAYS", nullable = false, precision = 4, scale = 1)
  private BigDecimal usedDays;

  @Column(name = "EXPIRE_DT", nullable = false)
  private LocalDate expireDt;

  protected LeaveGrant() {}

  private LeaveGrant(Long empId, int yearNo, BigDecimal grantDays, LocalDate expireDt) {
    this.empId = empId;
    this.yearNo = yearNo;
    this.grantDays = grantDays;
    this.usedDays = BigDecimal.ZERO;
    this.expireDt = expireDt;
  }

  public static LeaveGrant grant(Long empId, int yearNo, BigDecimal grantDays, LocalDate expireDt) {
    return new LeaveGrant(empId, yearNo, grantDays, expireDt);
  }

  public BigDecimal remainingDays() {
    return grantDays.subtract(usedDays);
  }

  /**
   * 연차 차감.
   *
   * @throws BusinessException 잔여일수보다 많이 쓰려 하면 {@link LeaveErrorCode#INSUFFICIENT_BALANCE}
   */
  public void consume(BigDecimal days) {
    if (days.compareTo(remainingDays()) > 0) {
      throw new BusinessException(
          LeaveErrorCode.INSUFFICIENT_BALANCE,
          "연차 잔여 " + remainingDays() + "일보다 많은 " + days + "일을 사용할 수 없습니다.");
    }
    this.usedDays = usedDays.add(days);
  }

  /** 승인 취소 시 차감 복원. */
  public void restore(BigDecimal days) {
    BigDecimal restored = usedDays.subtract(days);
    this.usedDays = restored.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : restored;
  }

  public Long getId() {
    return id;
  }

  public Long getEmpId() {
    return empId;
  }

  public int getYearNo() {
    return yearNo;
  }

  public BigDecimal getGrantDays() {
    return grantDays;
  }

  public BigDecimal getUsedDays() {
    return usedDays;
  }

  public LocalDate getExpireDt() {
    return expireDt;
  }
}
