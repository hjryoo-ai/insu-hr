package com.portfolio.insuhr.domain.leave;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.emp.Emp;
import com.portfolio.insuhr.domain.emp.EmpErrorCode;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 부여 (설계서 8 v2.0, {@code annualLeaveGrantJob}이 감싼다).
 *
 * <p><b>부여 모델은 회계연도 일괄로 확정</b>(설계서 8 v2.0) — §8 원안이 "매년 1/1 / 입사기념일" 둘을 병기했으나 이 둘은 다른 부여 모델(회계연도 일괄
 * vs 개별 기산)이다. 포트폴리오 범위에선 회계연도 일괄 하나로 고정한다: 기준일이 든 해({@code yearNo})의 연차를 재직자 전원에게 부여한다. 1년 미만자의 월
 * 단위 개별 기산은 범위 밖(단순화).
 *
 * <p><b>부여일수는 근속 기준 정책값</b>이다(법령 하드코딩 금지, 설계서 코딩규칙): 기본일수 + 근속가산(2년마다 +1 등), 상한 컷. 수치는 {@code
 * TB_POLICY_CONFIG}에서 읽는다.
 *
 * <p>재실행 멱등의 방어선은 스키마의 {@code UQ_LEAVE_GRANT(EMP_ID, YEAR_NO)}이고(설계서 V10), 이 서비스는 그 앞에서 존재 검사로 한 번
 * 더 막아 같은 {@code targetDate} 재실행이 조용히 no-op이 되게 한다.
 */
@Service
public class AnnualLeaveGrantService {

  private final EmpRepository empRepository;
  private final LeaveGrantRepository leaveGrantRepository;
  private final PolicyConfigService policyConfigService;

  public AnnualLeaveGrantService(
      EmpRepository empRepository,
      LeaveGrantRepository leaveGrantRepository,
      PolicyConfigService policyConfigService) {
    this.empRepository = empRepository;
    this.leaveGrantRepository = leaveGrantRepository;
    this.policyConfigService = policyConfigService;
  }

  /**
   * 기준일이 든 해의 연차를 한 직원에게 부여한다. 이미 그 해 부여가 있으면 no-op(멱등).
   *
   * @return 실제로 부여했으면 {@code true}
   */
  @Transactional
  public boolean grantFor(Long empId, LocalDate asOf) {
    int yearNo = asOf.getYear();
    if (leaveGrantRepository.existsByEmpIdAndYearNo(empId, yearNo)) {
      return false; // 같은 해 재실행 — UQ 앞에서 멱등 no-op
    }
    Emp emp =
        empRepository
            .findById(empId)
            .orElseThrow(
                () -> new BusinessException(EmpErrorCode.NOT_FOUND, "임직원을 찾을 수 없습니다: " + empId));

    BigDecimal days = computeGrantDays(emp.getHireDt(), yearNo, asOf);
    LocalDate expireDt = LocalDate.of(yearNo, 12, 31); // 회계연도 말 소멸(단순화)
    leaveGrantRepository.save(LeaveGrant.grant(empId, yearNo, days, expireDt));
    return true;
  }

  /**
   * 근속 기준 부여일수 = min(기본 + 가산, 상한). 가산 = max(0, 근속연수-1) / (가산주기). 근속연수는 회계연도 기준({@code yearNo -
   * 입사연도})으로 단순화한다.
   */
  BigDecimal computeGrantDays(LocalDate hireDt, int yearNo, LocalDate asOf) {
    int base = policyConfigService.getInt(PolicyKey.ANNUAL_LEAVE_BASE_DAYS, asOf);
    int max = policyConfigService.getInt(PolicyKey.ANNUAL_LEAVE_MAX_DAYS, asOf);
    int perYears = policyConfigService.getInt(PolicyKey.ANNUAL_LEAVE_BONUS_PER_YEARS, asOf);

    int tenure = yearNo - hireDt.getYear();
    int bonus = perYears > 0 ? Math.max(0, tenure - 1) / perYears : 0;
    int total = Math.min(base + bonus, max);
    return BigDecimal.valueOf(total);
  }
}
