package com.portfolio.insuhr.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.MutableClock;
import com.portfolio.insuhr.api.support.StubRequirementCheckerConfig;
import com.portfolio.insuhr.api.support.TestClockConfig;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.domain.agent.Agent;
import com.portfolio.insuhr.domain.agent.AgentRepository;
import com.portfolio.insuhr.domain.agent.AgentStatus;
import com.portfolio.insuhr.domain.agent.Association;
import com.portfolio.insuhr.domain.agent.Channel;
import com.portfolio.insuhr.domain.agent.GuaranteeStatus;
import com.portfolio.insuhr.domain.agent.LicenseStatus;
import com.portfolio.insuhr.domain.agent.LicenseType;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 동시 전이 방어 — 낙관적 잠금 (설계서 5.3 v1.5).
 *
 * <p>두 담당자가 같은 설계사에게 동시에 전이를 걸면, 사전조건 검사와 쓰기 사이의 틈에서 둘 다 성공하는 불법 상태가 생길 수 있다. {@code
 * TB_AGENT.VERSION} 낙관적 잠금이 이 틈을 닫는다 — 8스레드가 동시에 정지를 걸어도 정확히 하나만 이기고 나머지는 충돌로 실패한다. Phase 2의 동시 인물
 * 등록 테스트와 같은 패턴이다.
 */
@Import({TestClockConfig.class, StubRequirementCheckerConfig.class})
class AgentConcurrentTransitionTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired AgentService agentService;
  @Autowired AgentCredentialService credentialService;
  @Autowired AgentRepository agentRepository;
  @Autowired OrgRepository orgRepository;
  @Autowired MutableClock clock;

  @Test
  @DisplayName("8스레드가 동시에 정지를 걸어도 정확히 하나만 성공한다")
  void concurrentSuspendLetsOnlyOneWin() throws Exception {
    clock.setDate(LocalDate.of(2026, 3, 1));
    Long orgId =
        orgRepository
            .save(
                Org.create(
                    TestSeq.orgCd(), "조직", OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1)))
            .getId();

    // ACTIVE 상태까지 몰고 간다.
    int n = SEQ.getAndIncrement();
    AgentService.RegisterResult reg =
        agentService.registerCandidate(
            new NewPerson(
                "설계사" + n,
                TestSeq.rrn(),
                LocalDate.of(1988, 5, 5),
                Gender.F,
                "01000000000",
                null,
                "KR"),
            new AgentService.RegisterCommand(Channel.FC, orgId, null));
    // 활성화 워크플로 끝의 재판정이 실제 자격을 보므로, ACTIVE를 유지하려면 자격을 갖춰 둔다 (설계서 5.4 v1.6).
    credentialService.registerLicense(
        reg.agentId(), LicenseType.LIFE, "L", null, LocalDate.of(2026, 1, 1), LicenseStatus.VALID);
    credentialService.registerGuarantee(
        reg.agentId(),
        "SURETY_INS",
        new BigDecimal("10000000"),
        "보증사",
        "P",
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2036, 1, 1),
        GuaranteeStatus.ACTIVE);
    agentService.appoint(
        reg.agentId(),
        LocalDate.of(2026, 3, 1),
        new AgentService.ContractCommand("FC_STD", "2026-1", null, null, null));
    agentService.registerAssociation(
        reg.agentId(), LocalDate.of(2026, 3, 2), Association.LIFE_ASSOC, "L-1");

    int threads = 8;
    CyclicBarrier startTogether = new CyclicBarrier(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    AtomicInteger success = new AtomicInteger();
    AtomicInteger failure = new AtomicInteger();
    try {
      List<Callable<Void>> tasks =
          IntStream.range(0, threads)
              .<Callable<Void>>mapToObj(
                  i ->
                      () -> {
                        startTogether.await(10, TimeUnit.SECONDS);
                        try {
                          agentService.suspend(
                              reg.agentId(), LocalDate.of(2026, 3, 3), "EDU_OVERDUE", "동시 정지 " + i);
                          success.incrementAndGet();
                        } catch (Exception e) {
                          // 낙관적 잠금 충돌(진 스레드) 또는 이미 SUSPENDED를 본 스레드의 전이 거부.
                          failure.incrementAndGet();
                        }
                        return null;
                      })
              .toList();

      for (Future<Void> f : pool.invokeAll(tasks)) {
        f.get(30, TimeUnit.SECONDS);
      }

      assertThat(success.get()).as("정확히 한 전이만 이겨야 한다").isEqualTo(1);
      assertThat(failure.get()).as("나머지는 전부 실패해야 한다").isEqualTo(threads - 1);

      Agent finalState = agentRepository.findById(reg.agentId()).orElseThrow();
      assertThat(finalState.getStatus()).isEqualTo(AgentStatus.SUSPENDED);
    } finally {
      pool.shutdownNow();
    }
  }
}
