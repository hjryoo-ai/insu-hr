package com.portfolio.insuhr.domain.emp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.common.exception.BusinessException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 발령 문서 상태머신과 상태 파생 (설계서 5.5). DB 없이 도는 순수 단위 테스트 — 규칙만 검증한다. */
class AppointmentTest {

  private static final LocalDate D = LocalDate.of(2026, 9, 1);

  private Appointment draft(AppointType type) {
    return Appointment.draft(1L, type, D, 10L, "STAFF", null, EmpStatus.ACTIVE, "사유");
  }

  @Test
  @DisplayName("입사·휴직·복직·퇴직 발령은 재직상태를 강제한다")
  void statusIsDerivedFromType() {
    assertThat(draft(AppointType.HIRE).getStatus()).isEqualTo(EmpStatus.ACTIVE);
    assertThat(draft(AppointType.LEAVE).getStatus()).isEqualTo(EmpStatus.ON_LEAVE);
    assertThat(draft(AppointType.RETURN).getStatus()).isEqualTo(EmpStatus.ACTIVE);
    assertThat(draft(AppointType.RESIGN).getStatus()).isEqualTo(EmpStatus.RESIGNED);
  }

  @Test
  @DisplayName("승진·전보·겸직·파견은 재직상태를 바꾸지 않고 직전 상태를 물려받는다")
  void statusIsInheritedForNonStatusTypes() {
    // 직전 상태를 ON_LEAVE로 주면 그대로 물려받아야 한다 (휴직 중 전보 등).
    Appointment transfer =
        Appointment.draft(
            1L, AppointType.TRANSFER, D, 10L, "STAFF", null, EmpStatus.ON_LEAVE, null);
    assertThat(transfer.getStatus()).isEqualTo(EmpStatus.ON_LEAVE);
  }

  @Test
  @DisplayName("기안만 확정할 수 있고, 확정을 두 번 하면 거부된다")
  void onlyDraftCanBeConfirmed() {
    Appointment a = draft(AppointType.PROMOTION);
    assertThat(a.getDocStatus()).isEqualTo(AppointDocStatus.DRAFT);
    a.confirm();
    assertThat(a.getDocStatus()).isEqualTo(AppointDocStatus.CONFIRMED);
    assertThatThrownBy(a::confirm).isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("반영 여부는 CONFIRMED이고 발령일이 기준일 이하일 때만 참이다")
  void appliedIsDerivedNotStored() {
    Appointment a = draft(AppointType.TRANSFER);
    assertThat(a.isAppliedOn(D)).isFalse(); // 아직 DRAFT
    a.confirm();
    assertThat(a.isAppliedOn(D.minusDays(1))).isFalse(); // 발령일이 미래
    assertThat(a.isAppliedOn(D)).isTrue(); // 발령일 당일
    assertThat(a.isAppliedOn(D.plusDays(1))).isTrue(); // 발령일 이후
  }

  @Test
  @DisplayName("확정된 미래 발령은 취소 가능, 반영된 발령은 취소 불가")
  void cancelRespectsAppliedState() {
    Appointment future = draft(AppointType.TRANSFER);
    future.confirm();
    // 오늘이 발령일 하루 전이면 미반영 → 취소 가능
    assertThatCode(() -> future.cancel(D.minusDays(1))).doesNotThrowAnyException();
    assertThat(future.getDocStatus()).isEqualTo(AppointDocStatus.CANCELED);

    Appointment applied = draft(AppointType.TRANSFER);
    applied.confirm();
    // 오늘이 발령일 당일이면 이미 반영 → 취소 불가
    assertThatThrownBy(() -> applied.cancel(D))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("이미 반영된 발령");
  }
}
