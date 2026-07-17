package com.portfolio.insuhr.domain.org;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 조직 개편 이력 (설계서 6.5, 6.6).
 *
 * <p><b>BEFORE/AFTER_JSON은 diff가 아니라 전체 스냅샷이다</b>(설계서 6.6 v1.2). 기준일자 시점 조회를 diff 재생이 아니라 "조직별
 * {@code EFFECTIVE_DT <= 기준일} 중 최신 1건"을 골라 AFTER_JSON에서 꺼내는 것으로 풀기 위한 전제다.
 */
@Entity
@Table(name = "TB_ORG_HIST")
public class OrgHist extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ORG_HIST_ID")
  private Long id;

  @Column(name = "ORG_ID", nullable = false)
  private Long orgId;

  @Column(name = "CHANGE_TYPE_CD", nullable = false, length = 10)
  private String changeTypeCd;

  @Lob
  @Column(name = "BEFORE_JSON")
  private String beforeJson;

  @Lob
  @Column(name = "AFTER_JSON")
  private String afterJson;

  @Column(name = "EFFECTIVE_DT", nullable = false)
  private LocalDate effectiveDt;

  @Column(name = "RSN_TXT", length = 400)
  private String rsnTxt;

  protected OrgHist() {}

  private OrgHist(
      Long orgId,
      OrgChangeType changeType,
      String beforeJson,
      String afterJson,
      LocalDate effectiveDt,
      String rsnTxt) {
    this.orgId = orgId;
    this.changeTypeCd = changeType.name();
    this.beforeJson = beforeJson;
    this.afterJson = afterJson;
    this.effectiveDt = effectiveDt;
    this.rsnTxt = rsnTxt;
  }

  /**
   * 이력 1건.
   *
   * @param beforeJson 변경 전 전체 스냅샷. 신설이면 null
   * @param afterJson 변경 후 전체 스냅샷
   */
  public static OrgHist of(
      Long orgId,
      OrgChangeType changeType,
      String beforeJson,
      String afterJson,
      LocalDate effectiveDt,
      String rsnTxt) {
    return new OrgHist(orgId, changeType, beforeJson, afterJson, effectiveDt, rsnTxt);
  }

  public Long getId() {
    return id;
  }

  public Long getOrgId() {
    return orgId;
  }

  public String getChangeTypeCd() {
    return changeTypeCd;
  }

  public String getBeforeJson() {
    return beforeJson;
  }

  public String getAfterJson() {
    return afterJson;
  }

  public LocalDate getEffectiveDt() {
    return effectiveDt;
  }

  public String getRsnTxt() {
    return rsnTxt;
  }
}
