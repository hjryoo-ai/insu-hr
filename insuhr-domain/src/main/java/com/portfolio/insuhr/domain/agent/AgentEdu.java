package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 설계사 교육 이수 이력 (설계서 6.4).
 *
 * <p>보수교육({@link EduType#CONTINUING})은 {@code NEXT_DUE_DT = COMPLETE_DT + 주기}를 낳고, 이 값이 모집자격의 보수교육
 * 게이트가 된다(설계서 5.4). NEXT_DUE_DT 계산은 애플리케이션 서비스가 정책값 주기로 하며, 엔티티는 결과를 담는다.
 */
@Entity
@Table(name = "TB_AGENT_EDU")
public class AgentEdu extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "AGENT_EDU_ID")
  private Long id;

  @Column(name = "AGENT_ID", nullable = false)
  private Long agentId;

  @Column(name = "EDU_TYPE_CD", nullable = false, length = 30)
  private String eduTypeCd;

  @Column(name = "EDU_NM", nullable = false, length = 200)
  private String eduNm;

  @Column(name = "COMPLETE_DT", nullable = false)
  private LocalDate completeDt;

  @Column(name = "EDU_HOURS", nullable = false, precision = 5, scale = 1)
  private BigDecimal eduHours;

  @Column(name = "PROVIDER_NM", length = 100)
  private String providerNm;

  @Column(name = "NEXT_DUE_DT")
  private LocalDate nextDueDt;

  protected AgentEdu() {}

  private AgentEdu(
      Long agentId,
      EduType type,
      String eduNm,
      LocalDate completeDt,
      BigDecimal eduHours,
      String providerNm,
      LocalDate nextDueDt) {
    this.agentId = agentId;
    this.eduTypeCd = type.name();
    this.eduNm = eduNm;
    this.completeDt = completeDt;
    this.eduHours = eduHours;
    this.providerNm = providerNm;
    this.nextDueDt = nextDueDt;
  }

  public static AgentEdu complete(
      Long agentId,
      EduType type,
      String eduNm,
      LocalDate completeDt,
      BigDecimal eduHours,
      String providerNm,
      LocalDate nextDueDt) {
    return new AgentEdu(agentId, type, eduNm, completeDt, eduHours, providerNm, nextDueDt);
  }

  public Long getId() {
    return id;
  }

  public Long getAgentId() {
    return agentId;
  }

  public EduType getType() {
    return EduType.valueOf(eduTypeCd);
  }

  public LocalDate getCompleteDt() {
    return completeDt;
  }

  public BigDecimal getEduHours() {
    return eduHours;
  }

  public LocalDate getNextDueDt() {
    return nextDueDt;
  }

  public String getEduNm() {
    return eduNm;
  }

  public String getProviderNm() {
    return providerNm;
  }
}
