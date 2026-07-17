package com.portfolio.insuhr.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.MutableClock;
import com.portfolio.insuhr.api.support.TestClockConfig;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.agent.Channel;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 도입 계보 조회 + 순환 방어 (설계서 7.2, 5.3 v1.5).
 *
 * <p>A→B→C 도입 체인을 만들어 {@code CONNECT BY} 트리 조립을 검증하고, 하위 계보를 도입자로 지정하려는 순환 시도를 방어가 막는지 본다. Phase
 * 4에는 도입자 재지정 엔드포인트가 없어 순환이 API로 도달 불가하지만, 방어({@code assertRecruiterAssignable})와 조회 {@code
 * NOCYCLE}은 지금 넣어 두고 검증한다.
 */
@Import(TestClockConfig.class)
class AgentGenealogyIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired AgentService agentService;
  @Autowired OrgRepository orgRepository;
  @Autowired MutableClock clock;

  @BeforeEach
  void setDate() {
    clock.setDate(LocalDate.of(2026, 3, 1));
  }

  private Long newOrg() {
    return orgRepository
        .save(Org.create(TestSeq.orgCd(), "조직", OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1)))
        .getId();
  }

  private AgentService.RegisterResult register(Long orgId, String recruiterAgentCd) {
    int n = SEQ.getAndIncrement();
    return agentService.registerCandidate(
        new NewPerson(
            "설계사" + n,
            TestSeq.rrn(),
            LocalDate.of(1988, 5, 5),
            Gender.F,
            "01000000000",
            null,
            "KR"),
        new AgentService.RegisterCommand(Channel.FC, orgId, recruiterAgentCd));
  }

  @Test
  @DisplayName("A→B→C 도입 체인이 계보 트리로 조립된다")
  void genealogyAssemblesChain() {
    Long org = newOrg();
    AgentService.RegisterResult a = register(org, null);
    AgentService.RegisterResult b = register(org, a.agentCd());
    AgentService.RegisterResult c = register(org, b.agentCd());

    AgentService.GenealogyNode root = agentService.genealogy(a.agentId());

    assertThat(root.agentId()).isEqualTo(a.agentId());
    assertThat(root.children()).hasSize(1);
    AgentService.GenealogyNode nodeB = root.children().get(0);
    assertThat(nodeB.agentId()).isEqualTo(b.agentId());
    assertThat(nodeB.children()).hasSize(1);
    assertThat(nodeB.children().get(0).agentId()).isEqualTo(c.agentId());
  }

  @Test
  @DisplayName("하위 계보를 도입자로 지정하려는 순환 시도는 거부된다")
  void recruiterCycleRejected() {
    Long org = newOrg();
    AgentService.RegisterResult a = register(org, null);
    AgentService.RegisterResult b = register(org, a.agentCd());
    AgentService.RegisterResult c = register(org, b.agentCd());

    // C는 A의 하위 계보다. C를 A의 도입자로 지정하면 A→B→C→A 순환이 된다.
    assertThatThrownBy(() -> agentService.assertRecruiterAssignable(a.agentId(), c.agentId()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("순환");

    // 계보 밖의 설계사를 도입자로 지정하는 것은 순환이 아니다.
    AgentService.RegisterResult unrelated = register(newOrg(), null);
    assertThatCode(() -> agentService.assertRecruiterAssignable(a.agentId(), unrelated.agentId()))
        .doesNotThrowAnyException();
  }
}
