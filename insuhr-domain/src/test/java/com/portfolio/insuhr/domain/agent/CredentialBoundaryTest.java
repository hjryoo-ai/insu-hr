package com.portfolio.insuhr.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 재정보증·제재의 기간 경계 판정 단위 테스트 (설계서 5.4 v1.6 — 경계 inclusive).
 *
 * <p>모집자격 판정이 경계일에 뒤집히는 버그가 가장 흔하므로, 엔티티 술어를 DB 없이 못 박는다.
 */
class CredentialBoundaryTest {

  private FinGuarantee guarantee(LocalDate start, LocalDate end, GuaranteeStatus status) {
    return FinGuarantee.register(
        1L, "SURETY_INS", new BigDecimal("10000000"), "보증사", "P", start, end, status);
  }

  @Test
  @DisplayName("재정보증은 START_DT·END_DT 당일 모두 유효하다 (양끝 포함)")
  void guaranteeInclusiveBothEnds() {
    FinGuarantee g =
        guarantee(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30), GuaranteeStatus.ACTIVE);

    assertThat(g.isActiveOn(LocalDate.of(2025, 12, 31))).as("시작 전날 무효").isFalse();
    assertThat(g.isActiveOn(LocalDate.of(2026, 1, 1))).as("시작 당일 유효").isTrue();
    assertThat(g.isActiveOn(LocalDate.of(2026, 6, 30))).as("만기 당일 유효").isTrue();
    assertThat(g.isActiveOn(LocalDate.of(2026, 7, 1))).as("만기 다음날 무효").isFalse();
  }

  @Test
  @DisplayName("상태가 ACTIVE가 아니면 기간 내라도 무효다")
  void guaranteeInactiveStatusFails() {
    FinGuarantee expired =
        guarantee(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), GuaranteeStatus.EXPIRED);
    assertThat(expired.isActiveOn(LocalDate.of(2026, 6, 1))).isFalse();
  }

  @Test
  @DisplayName("제재는 START·END 당일 모두 모집정지, END_DT null은 무기한")
  void sanctionBlockingBoundary() {
    AgentSanction bounded =
        AgentSanction.impose(
            1L,
            "FSS",
            "RECRUIT_STOP",
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 5, 31),
            "제재",
            true);

    assertThat(bounded.isBlockingOn(LocalDate.of(2026, 1, 31))).as("시작 전날").isFalse();
    assertThat(bounded.isBlockingOn(LocalDate.of(2026, 2, 1))).as("시작 당일").isTrue();
    assertThat(bounded.isBlockingOn(LocalDate.of(2026, 5, 31))).as("종료 당일").isTrue();
    assertThat(bounded.isBlockingOn(LocalDate.of(2026, 6, 1))).as("종료 다음날").isFalse();

    AgentSanction openEnded =
        AgentSanction.impose(
            1L, "FSS", "RECRUIT_STOP", LocalDate.of(2026, 2, 1), null, "무기한", true);
    assertThat(openEnded.isBlockingOn(LocalDate.of(2030, 1, 1))).as("END null=무기한").isTrue();
  }

  @Test
  @DisplayName("RECRUIT_BLOCK_YN=N 제재는 기간 내라도 모집을 막지 않는다")
  void nonBlockingSanction() {
    AgentSanction warning =
        AgentSanction.impose(
            1L,
            "ASSOC",
            "WARNING",
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 12, 31),
            "경고",
            false);
    assertThat(warning.isBlockingOn(LocalDate.of(2026, 6, 1))).isFalse();
  }
}
