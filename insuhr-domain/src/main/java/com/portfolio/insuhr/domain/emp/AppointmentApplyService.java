package com.portfolio.insuhr.domain.emp;

import com.portfolio.insuhr.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발령 → 임직원 스냅샷 반영 규칙 (설계서 5.5 v1.4, 시나리오 6a).
 *
 * <p><b>이 클래스가 Phase 3의 위험 전부다.</b> 인사기록카드 6종은 단순 CRUD지만, 날짜 경계·같은 날 다중 발령·취소와의 상호작용은 전부 여기서 갈린다.
 *
 * <h2>왜 증분 적용이 아니라 재계산인가</h2>
 *
 * <p>"발령이 도래할 때마다 현재 스냅샷에 덮어쓴다"는 증분 방식은 세 가지 버그를 각각 만든다 — 배치 재실행 시 중복 적용, 중복 실행 시 경합, 같은 날 다중 발령의
 * 순서 비결정성. 대신 스냅샷을 기준일의 함수로 정의하면 셋 다 사라진다:
 *
 * <pre>
 *   스냅샷(직원, D) = CONFIRMED 이고 APPOINT_DT &lt;= D 인 발령 중
 *                     (APPOINT_DT, APPOINT_ID) 최대인 행
 * </pre>
 *
 * <p>이 정의에서 <b>멱등성이 공짜로 나온다</b> — 몇 번을 계산해도 입력이 같으면 결과가 같다. Phase 7이 8장의 "재실행 안전" 요구를 따로 구현할 필요가 없는
 * 이유이고, {@code futureAppointApplyJob}이 이 서비스를 감싸기만 하면 되는 이유다.
 *
 * <p>또 하나 따라 나오는 성질: <b>확정 순서가 결과에 영향을 주지 않는다.</b> 8/1 승진을 9/1 전보보다 나중에 확정해도, 스냅샷은 확정 시각이 아니라 발령일로
 * 정렬되므로 결과가 같다. 순수 함수라 "어느 것을 먼저 확정했는가"라는 상태가 아예 없다.
 */
@Service
public class AppointmentApplyService {

  private final EmpRepository empRepository;
  private final AppointmentRepository appointmentRepository;
  private final Clock clock;

  public AppointmentApplyService(
      EmpRepository empRepository, AppointmentRepository appointmentRepository, Clock clock) {
    this.empRepository = empRepository;
    this.appointmentRepository = appointmentRepository;
    this.clock = clock;
  }

  /** 업무 기준일. {@code LocalDate.now()}를 직접 부르지 않는 이유는 {@code ClockConfig} 참조. */
  public LocalDate today() {
    return LocalDate.now(clock);
  }

  /**
   * 기준일 시점의 스냅샷을 계산한다 — 부수효과 없음.
   *
   * @return 그 시점에 반영된 발령이 하나도 없으면 empty (입사 발령 확정 전)
   */
  public Optional<EmpSnapshot> snapshotAsOf(Long empId, LocalDate asOf) {
    return appointmentRepository.findSnapshotAppointment(empId, asOf).map(EmpSnapshot::of);
  }

  /**
   * 오늘 기준으로 직원 한 명의 스냅샷을 다시 계산해 {@code TB_EMP}에 물질화한다.
   *
   * <p>몇 번을 호출해도 결과가 같다 — 계산이 호출 횟수가 아니라 (직원, 기준일)에만 의존하기 때문이다.
   *
   * @return 실제로 값이 바뀌었으면 true. 호출부(온라인 확정/배치)가 이벤트 발행 여부를 판단하는 데 쓴다
   */
  @Transactional
  public boolean recalculate(Long empId) {
    return recalculateAsOf(empId, today());
  }

  /**
   * 기준일을 명시한 재계산. 배치의 {@code --targetDate} 재처리용.
   *
   * <p><b>{@code @Transactional}이 필수다.</b> {@link Emp#applySnapshot}은 조회한 엔티티를 변경만 하고 저장 호출을 하지
   * 않는다(더티 체킹에 맡긴다). 트랜잭션이 없으면 로드한 엔티티가 영속 상태가 아니라 변경이 flush되지 않아 DB에 반영되지 않는다 — {@code
   * EmployeeService.confirmAppointment}처럼 상위 트랜잭션 안에서 불릴 때는 그 트랜잭션에 합류하지만, 배치나 테스트가 직접 부를 때는 여기서
   * 트랜잭션을 열어야 한다.
   */
  @Transactional
  public boolean recalculateAsOf(Long empId, LocalDate asOf) {
    Emp emp =
        empRepository
            .findById(empId)
            .orElseThrow(
                () -> new BusinessException(EmpErrorCode.NOT_FOUND, "임직원을 찾을 수 없습니다: " + empId));

    return snapshotAsOf(empId, asOf).map(emp::applySnapshot).orElse(false);
  }

  /**
   * 발령일이 도래한 확정 발령을 가진 직원 목록 (Phase 7 배치의 Reader가 쓸 대상 추출).
   *
   * <p>발령 목록이 아니라 직원 목록인 것이 핵심이다 — 반영은 발령 단위 적용이 아니라 직원 단위 재계산이라, 같은 날 발령이 여러 건이어도 한 번만 계산한다.
   */
  public List<Long> findEmpIdsDueOn(LocalDate appointDt) {
    return appointmentRepository.findEmpIdsWithAppointmentDueOn(appointDt);
  }
}
