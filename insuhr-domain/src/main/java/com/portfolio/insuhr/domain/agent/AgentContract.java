package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 위촉계약 (설계서 6.5, 부록 B 5단계).
 *
 * <p>위촉(appoint) 성공 시 생성된다. 지급계좌는 암호문({@code ACCOUNT_ENC})으로만 저장하고, 복호화는 Phase 5의 수수료 연계에서만 한다(설계서
 * 6.8). 유효기간형 이력(6.6)이라 현재 계약은 {@code VALID_TO_DT = 9999-12-31}.
 *
 * <p>계좌 암호화는 애플리케이션 서비스가 {@code AesGcmCipher}로 해서 넘긴다 — 엔티티는 암호문을 담기만 한다(도메인이 암호화 키를 알 필요가 없다).
 */
@Entity
@Table(name = "TB_AGENT_CONTRACT")
public class AgentContract extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "CONTRACT_ID")
  private Long id;

  @Column(name = "AGENT_ID", nullable = false)
  private Long agentId;

  @Column(name = "CONTRACT_TYPE_CD", nullable = false, length = 30)
  private String contractTypeCd;

  @Column(name = "CONTRACT_DT", nullable = false)
  private LocalDate contractDt;

  @Column(name = "COMM_RULE_VER", length = 30)
  private String commRuleVer;

  @Column(name = "BANK_CD", length = 30)
  private String bankCd;

  @Column(name = "ACCOUNT_ENC", length = 256)
  private String accountEnc;

  @Column(name = "ACCOUNT_HOLDER_NM", length = 100)
  private String accountHolderNm;

  @Column(name = "VALID_FROM_DT", nullable = false)
  private LocalDate validFromDt;

  @Column(name = "VALID_TO_DT", nullable = false)
  private LocalDate validToDt;

  protected AgentContract() {}

  private AgentContract(
      Long agentId,
      String contractTypeCd,
      LocalDate contractDt,
      String commRuleVer,
      String bankCd,
      String accountEnc,
      String accountHolderNm,
      LocalDate validFromDt) {
    this.agentId = agentId;
    this.contractTypeCd = contractTypeCd;
    this.contractDt = contractDt;
    this.commRuleVer = commRuleVer;
    this.bankCd = bankCd;
    this.accountEnc = accountEnc;
    this.accountHolderNm = accountHolderNm;
    this.validFromDt = validFromDt;
    this.validToDt = LocalDate.of(9999, 12, 31);
  }

  /**
   * 위촉계약을 생성한다.
   *
   * @param accountEnc 지급계좌 암호문. 원문이 아니라 {@code AesGcmCipher}로 암호화된 값을 받는다
   */
  public static AgentContract create(
      Long agentId,
      String contractTypeCd,
      LocalDate contractDt,
      String commRuleVer,
      String bankCd,
      String accountEnc,
      String accountHolderNm,
      LocalDate validFromDt) {
    return new AgentContract(
        agentId,
        contractTypeCd,
        contractDt,
        commRuleVer,
        bankCd,
        accountEnc,
        accountHolderNm,
        validFromDt);
  }

  public Long getId() {
    return id;
  }

  public Long getAgentId() {
    return agentId;
  }

  public String getContractTypeCd() {
    return contractTypeCd;
  }

  public LocalDate getContractDt() {
    return contractDt;
  }

  public String getCommRuleVer() {
    return commRuleVer;
  }

  public String getBankCd() {
    return bankCd;
  }

  public String getAccountEnc() {
    return accountEnc;
  }

  public String getAccountHolderNm() {
    return accountHolderNm;
  }

  public LocalDate getValidFromDt() {
    return validFromDt;
  }

  public LocalDate getValidToDt() {
    return validToDt;
  }
}
