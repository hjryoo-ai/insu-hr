package com.portfolio.insuhr.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.common.crypto.AesGcmCipher;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.audit.PrivacyAccessLog;
import com.portfolio.insuhr.domain.audit.PrivacyAccessLogRepository;
import com.portfolio.insuhr.domain.auth.UserAccount;
import com.portfolio.insuhr.domain.auth.UserAccountRepository;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 지급계좌 복호화 — 원문+마스킹 동시 반환, 접근로그 동반, 사유 필수 (설계서 7.2 백로그, 10.2 Phase 8).
 *
 * <p>RRN 복호화 테스트(PersonIntegrationTest)와 같은 계약을 계좌에 대해 확인한다.
 */
class AgentAccountIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired AgentAccountService agentAccountService;
  @Autowired com.portfolio.insuhr.api.person.PersonService personService;
  @Autowired PrivacyAccessLogRepository accessLogRepository;
  @Autowired AesGcmCipher cipher;
  @Autowired JdbcClient jdbcClient;
  @Autowired UserAccountRepository userRepository;

  private String uniq() {
    return String.format("%06d", SEQ.getAndIncrement());
  }

  private Long anyUserId() {
    return userRepository
        .save(UserAccount.ofHuman("acct-test-" + uniq(), "{bcrypt}dummy", null))
        .getId();
  }

  private long seedOrg() {
    String cd = "O" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_ORG (ORG_CD, ORG_NM, ORG_TYPE_CD, ORG_LVL, VALID_FROM_DT, CREATED_BY)"
                + " VALUES (:cd, '조직', 'BRANCH', 1, DATE '2020-01-01', 'TEST')")
        .param("cd", cd)
        .update();
    return jdbcClient
        .sql("SELECT ORG_ID FROM TB_ORG WHERE ORG_CD = :cd")
        .param("cd", cd)
        .query(Long.class)
        .single();
  }

  private long seedAgent(long personId, long orgId) {
    String cd = "A" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_AGENT (PERSON_ID, AGENT_CD, CHANNEL_CD, ORG_ID, AGENT_STATUS_CD,"
                + " FIRST_APPOINT_DT, LAST_APPOINT_DT, RECRUIT_ELIG_YN, CREATED_BY)"
                + " VALUES (:pid, :cd, 'FC', :org, 'ACTIVE', DATE '2026-01-01', DATE '2026-01-01',"
                + " 'Y', 'TEST')")
        .param("pid", personId)
        .param("cd", cd)
        .param("org", orgId)
        .update();
    return jdbcClient
        .sql("SELECT AGENT_ID FROM TB_AGENT WHERE AGENT_CD = :cd")
        .param("cd", cd)
        .query(Long.class)
        .single();
  }

  private void seedContractWithAccount(long agentId, String account) {
    jdbcClient
        .sql(
            "INSERT INTO TB_AGENT_CONTRACT (AGENT_ID, CONTRACT_TYPE_CD, CONTRACT_DT, BANK_CD,"
                + " ACCOUNT_ENC, ACCOUNT_HOLDER_NM, VALID_FROM_DT, CREATED_BY)"
                + " VALUES (:a, 'FC_STD', DATE '2026-01-01', '004', :enc, '김설계',"
                + " DATE '2026-01-01', 'TEST')")
        .param("a", agentId)
        .param("enc", cipher.encrypt(account))
        .update();
  }

  private long seedPerson() {
    return personService
        .register(
            new NewPerson(
                "김설계",
                "900101-" + uniq() + "0",
                LocalDate.of(1990, 1, 1),
                Gender.M,
                "01012341234",
                null,
                "KR"))
        .personId();
  }

  @Test
  @DisplayName("계좌 복호화는 원문+즉석 마스킹을 함께 주고 접근로그를 같은 트랜잭션에 남긴다")
  void decryptReturnsPlainAndMaskedWithAccessLog() {
    long personId = seedPerson();
    long orgId = seedOrg();
    long agentId = seedAgent(personId, orgId);
    seedContractWithAccount(agentId, "1234567890");
    Long actorId = anyUserId();

    AgentAccountService.AccountDecryptResult result =
        agentAccountService.decryptAccount(
            agentId, actorId, "수수료 정산 대사", "POST /test", "127.0.0.1");

    assertThat(result.account()).isEqualTo("1234567890");
    assertThat(result.accountMasked()).isEqualTo("******7890"); // 즉석 마스킹(수신 UI 재마스킹 방지)
    assertThat(result.bankCd()).isEqualTo("004");

    // 접근로그는 인물 기준(TARGET_PERSON_ID), 계약이 아니라 설계사의 PERSON_ID로 남는다.
    List<PrivacyAccessLog> logs =
        accessLogRepository.findByTargetPersonIdOrderByAccessAtDesc(personId);
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getAccessTypeCd()).isEqualTo("DECRYPT");
    assertThat(logs.get(0).getPurposeTxt()).isEqualTo("수수료 정산 대사");
  }

  @Test
  @DisplayName("사유 없이 복호화하면 거부되고 접근로그도 남지 않는다")
  void rejectsWithoutPurpose() {
    long personId = seedPerson();
    long agentId = seedAgent(personId, seedOrg());
    seedContractWithAccount(agentId, "1234567890");

    assertThatThrownBy(
            () ->
                agentAccountService.decryptAccount(
                    agentId, anyUserId(), "  ", "POST /test", "127.0.0.1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("사유");

    assertThat(accessLogRepository.findByTargetPersonIdOrderByAccessAtDesc(personId)).isEmpty();
  }

  @Test
  @DisplayName("계약(계좌)이 없으면 404 성격의 도메인 예외")
  void failsWhenNoAccount() {
    long personId = seedPerson();
    long agentId = seedAgent(personId, seedOrg()); // 계약 미생성

    assertThatThrownBy(
            () ->
                agentAccountService.decryptAccount(
                    agentId, anyUserId(), "정산", "POST /test", "127.0.0.1"))
        .isInstanceOf(BusinessException.class);
  }
}
