package com.portfolio.insuhr.domain.emp;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

  List<Appointment> findByEmpIdOrderByAppointDtDescIdDesc(Long empId);

  /**
   * 스냅샷 함수의 본체 (설계서 5.5 v1.4).
   *
   * <p>{@code CONFIRMED} 이고 {@code APPOINT_DT <= :asOf} 인 발령 중 {@code (APPOINT_DT, APPOINT_ID)}가 가장
   * 큰 행. 정렬의 두 번째 키가 결정성의 근거다 — 같은 날 발령이 여러 건이면 기안 순서(= ID 순서)로 승자가 정해진다. 이 키가 없으면 같은 날 다중 발령의 결과가
   * DB의 행 반환 순서에 좌우된다.
   *
   * <p>{@code LIMIT 1} 대신 리스트를 받는 이유: JPQL에 표준 LIMIT이 없어 {@code Pageable}을 쓰거나 목록을 잘라야 하는데, 호출부가
   * {@code Optional}만 필요로 하므로 {@link #findSnapshotAppointment}가 감싼다.
   */
  @Query(
      """
      SELECT a FROM Appointment a
       WHERE a.empId = :empId
         AND a.docStatusCd = 'CONFIRMED'
         AND a.appointDt <= :asOf
       ORDER BY a.appointDt DESC, a.id DESC
      """)
  List<Appointment> findConfirmedUpTo(@Param("empId") Long empId, @Param("asOf") LocalDate asOf);

  /** 기준일 스냅샷을 만드는 발령 1건. */
  default Optional<Appointment> findSnapshotAppointment(Long empId, LocalDate asOf) {
    return findConfirmedUpTo(empId, asOf).stream().findFirst();
  }

  /**
   * 발령일이 도래한 확정 발령의 직원 목록 (Phase 7 {@code futureAppointApplyJob}의 대상 추출).
   *
   * <p>발령 자체가 아니라 {@code EMP_ID}만 뽑는 이유: 반영은 발령을 하나씩 적용하는 것이 아니라 직원별 재계산이다(설계서 5.5). 같은 날 발령이 3건
   * 있어도 재계산은 1회면 된다.
   */
  @Query(
      """
      SELECT DISTINCT a.empId FROM Appointment a
       WHERE a.docStatusCd = 'CONFIRMED'
         AND a.appointDt = :appointDt
      """)
  List<Long> findEmpIdsWithAppointmentDueOn(@Param("appointDt") LocalDate appointDt);
}
