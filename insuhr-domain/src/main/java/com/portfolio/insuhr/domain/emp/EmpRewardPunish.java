package com.portfolio.insuhr.domain.emp;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 인사기록카드 — 상벌 (설계서 6.5).
 *
 * <p>설계사 제재({@code TB_AGENT_SANCTION}, Phase 5)와 별개다. 그쪽은 모집자격을 떨어뜨리지만 이쪽은 기록으로만 남는다.
 */
@Entity
@Table(name = "TB_EMP_REWARD_PUNISH")
public class EmpRewardPunish extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "RP_ID")
  private Long id;

  @Column(name = "EMP_ID", nullable = false)
  private Long empId;

  @Column(name = "RP_TYPE_CD", nullable = false, length = 30)
  private String rpTypeCd;

  @Column(name = "RP_CD", nullable = false, length = 30)
  private String rpCd;

  @Column(name = "RP_DT", nullable = false)
  private LocalDate rpDt;

  @Column(name = "RSN_TXT", length = 400)
  private String rsnTxt;

  protected EmpRewardPunish() {}

  public EmpRewardPunish(Long empId, String rpTypeCd, String rpCd, LocalDate rpDt, String rsnTxt) {
    this.empId = empId;
    this.rpTypeCd = rpTypeCd;
    this.rpCd = rpCd;
    this.rpDt = rpDt;
    this.rsnTxt = rsnTxt;
  }

  public void update(String rpTypeCd, String rpCd, LocalDate rpDt, String rsnTxt) {
    this.rpTypeCd = rpTypeCd;
    this.rpCd = rpCd;
    this.rpDt = rpDt;
    this.rsnTxt = rsnTxt;
  }

  public Long getId() {
    return id;
  }

  public Long getEmpId() {
    return empId;
  }

  public String getRpTypeCd() {
    return rpTypeCd;
  }

  public String getRpCd() {
    return rpCd;
  }

  public LocalDate getRpDt() {
    return rpDt;
  }

  public String getRsnTxt() {
    return rsnTxt;
  }
}
