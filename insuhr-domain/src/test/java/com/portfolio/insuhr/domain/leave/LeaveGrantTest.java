package com.portfolio.insuhr.domain.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 연차 잔여·차감 규칙 (설계서 6.5). DB 없이 도는 순수 단위 테스트. */
class LeaveGrantTest {

  private LeaveGrant grant(String days) {
    return LeaveGrant.grant(1L, 2026, new BigDecimal(days), LocalDate.of(2026, 12, 31));
  }

  @Test
  @DisplayName("잔여는 부여 - 사용으로 파생된다")
  void remainingIsGrantMinusUsed() {
    LeaveGrant g = grant("15.0");
    assertThat(g.remainingDays()).isEqualByComparingTo("15.0");
    g.consume(new BigDecimal("3.5"));
    assertThat(g.remainingDays()).isEqualByComparingTo("11.5");
  }

  @Test
  @DisplayName("잔여보다 많이 차감하려 하면 거부된다")
  void cannotConsumeMoreThanRemaining() {
    LeaveGrant g = grant("2.0");
    assertThatThrownBy(() -> g.consume(new BigDecimal("3.0")))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("잔여");
  }

  @Test
  @DisplayName("복원은 사용일수를 되돌리되 0 밑으로 내려가지 않는다")
  void restoreDoesNotGoNegative() {
    LeaveGrant g = grant("15.0");
    g.consume(new BigDecimal("5.0"));
    g.restore(new BigDecimal("5.0"));
    assertThat(g.getUsedDays()).isEqualByComparingTo("0.0");
    // 과다 복원 방어 — 이미 0인데 또 복원해도 음수가 되지 않는다.
    g.restore(new BigDecimal("3.0"));
    assertThat(g.getUsedDays()).isEqualByComparingTo("0.0");
  }
}
