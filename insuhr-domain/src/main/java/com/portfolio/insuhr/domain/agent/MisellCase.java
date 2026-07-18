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
 * 불완전판매 건 (설계서 6.5).
 *
 * <p>제재로 이어질 수 있어 {@code LINKED_SANCTION_ID}로 제재와 연결한다. 제재가 걸리면 그 제재가 모집자격 게이트에 영향을 주므로, 불완전판매 자체는
 * 기록이고 자격 영향은 연결된 제재를 통한다.
 */
@Entity
@Table(name = "TB_MISSELL_CASE")
public class MisellCase extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "MISSELL_ID")
  private Long id;

  @Column(name = "AGENT_ID", nullable = false)
  private Long agentId;

  @Column(name = "CONTRACT_NO", length = 50)
  private String contractNo;

  @Column(name = "CASE_TYPE_CD", nullable = false, length = 30)
  private String caseTypeCd;

  @Column(name = "OCCUR_DT", nullable = false)
  private LocalDate occurDt;

  @Column(name = "RESOLVE_DT")
  private LocalDate resolveDt;

  @Column(name = "RESULT_CD", length = 30)
  private String resultCd;

  @Column(name = "LINKED_SANCTION_ID")
  private Long linkedSanctionId;

  protected MisellCase() {}

  private MisellCase(Long agentId, String contractNo, String caseTypeCd, LocalDate occurDt) {
    this.agentId = agentId;
    this.contractNo = contractNo;
    this.caseTypeCd = caseTypeCd;
    this.occurDt = occurDt;
  }

  public static MisellCase open(
      Long agentId, String contractNo, String caseTypeCd, LocalDate occurDt) {
    return new MisellCase(agentId, contractNo, caseTypeCd, occurDt);
  }

  public void resolve(LocalDate resolveDt, String resultCd, Long linkedSanctionId) {
    this.resolveDt = resolveDt;
    this.resultCd = resultCd;
    this.linkedSanctionId = linkedSanctionId;
  }

  public Long getId() {
    return id;
  }

  public Long getAgentId() {
    return agentId;
  }

  public String getCaseTypeCd() {
    return caseTypeCd;
  }

  public LocalDate getOccurDt() {
    return occurDt;
  }

  public Long getLinkedSanctionId() {
    return linkedSanctionId;
  }
}
