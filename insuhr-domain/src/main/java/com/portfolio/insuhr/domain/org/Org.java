package com.portfolio.insuhr.domain.org;

import com.portfolio.insuhr.domain.support.BaseEntity;
import com.portfolio.insuhr.domain.support.YnConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 조직 (설계서 6.4).
 *
 * <p>본사 조직(부·팀)과 영업 조직(지역단–지점–영업소)을 하나의 자기참조 계층으로 다룬다.
 *
 * <p>유효기간형 이력(설계서 6.6): 현재 행은 {@code VALID_TO_DT = 9999-12-31}. 폐지는 삭제가 아니라 종료일을 닫는 것이다 — 과거 조직에
 * 소속됐던 발령·위촉 이력이 참조를 잃으면 안 된다.
 */
@Entity
@Table(name = "TB_ORG")
public class Org extends BaseEntity {

  /** 유효기간형 이력의 '현재 행' 표식 (설계서 6.6). */
  public static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "ORG_ID")
  private Long id;

  @Column(name = "ORG_CD", nullable = false, length = 10)
  private String orgCd;

  @Column(name = "ORG_NM", nullable = false, length = 100)
  private String orgNm;

  @Column(name = "ORG_TYPE_CD", nullable = false, length = 10)
  private String orgTypeCd;

  @Column(name = "UP_ORG_ID")
  private Long upOrgId;

  @Column(name = "ORG_LVL", nullable = false)
  private int orgLvl;

  @Column(name = "SORT_ORD", nullable = false)
  private int sortOrd;

  @Column(name = "VALID_FROM_DT", nullable = false)
  private LocalDate validFromDt;

  @Column(name = "VALID_TO_DT", nullable = false)
  private LocalDate validToDt;

  // 설계서 6.1의 _YN은 CHAR(1)이다. @JdbcTypeCode가 없으면 Hibernate가 VARCHAR2를 기대해
  // ddl-auto=validate가 기동을 막는다 (YnConverter 주석 참조).
  @Convert(converter = YnConverter.class)
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "USE_YN", nullable = false, length = 1)
  private boolean useYn;

  protected Org() {}

  private Org(
      String orgCd,
      String orgNm,
      OrgType orgType,
      Long upOrgId,
      int orgLvl,
      int sortOrd,
      LocalDate validFromDt) {
    this.orgCd = orgCd;
    this.orgNm = orgNm;
    this.orgTypeCd = orgType.name();
    this.upOrgId = upOrgId;
    this.orgLvl = orgLvl;
    this.sortOrd = sortOrd;
    this.validFromDt = validFromDt;
    this.validToDt = MAX_DATE;
    this.useYn = true;
  }

  /**
   * 조직 신설.
   *
   * @param parent 상위 조직. 루트면 null. 계층 깊이는 상위에서 파생되므로 호출부가 지정하지 않는다
   */
  public static Org create(
      String orgCd, String orgNm, OrgType orgType, Org parent, int sortOrd, LocalDate validFromDt) {
    if (parent != null && !parent.isActiveOn(validFromDt)) {
      throw new IllegalArgumentException("폐지된 조직 아래에 신설할 수 없습니다. 상위조직=" + parent.getOrgCd());
    }
    int level = parent == null ? 1 : parent.getOrgLvl() + 1;
    Long upOrgId = parent == null ? null : parent.getId();
    return new Org(orgCd, orgNm, orgType, upOrgId, level, sortOrd, validFromDt);
  }

  /** 기준일에 유효한 조직인가. */
  public boolean isActiveOn(LocalDate asOf) {
    return useYn && !asOf.isBefore(validFromDt) && !asOf.isAfter(validToDt);
  }

  public boolean isClosed() {
    return !MAX_DATE.equals(validToDt) || !useYn;
  }

  /** 명칭 변경. */
  public void rename(String newName) {
    if (isClosed()) {
      throw new IllegalStateException("폐지된 조직은 변경할 수 없습니다. 조직코드=" + orgCd);
    }
    this.orgNm = newName;
  }

  /**
   * 상위 조직 이관.
   *
   * <p>하위 트리의 ORG_LVL도 따라 바뀌어야 하지만, 그것은 이 엔티티 혼자 알 수 없다(자식들을 모른다). 트리 전체 갱신은 {@code OrgService}가
   * 담당한다.
   */
  public void moveTo(Org newParent) {
    if (isClosed()) {
      throw new IllegalStateException("폐지된 조직은 이관할 수 없습니다. 조직코드=" + orgCd);
    }
    if (newParent != null && newParent.getId().equals(this.id)) {
      throw new IllegalArgumentException("자기 자신을 상위 조직으로 지정할 수 없습니다.");
    }
    this.upOrgId = newParent == null ? null : newParent.getId();
    this.orgLvl = newParent == null ? 1 : newParent.getOrgLvl() + 1;
  }

  /** 계층 깊이 재설정 (상위 이관에 따른 하위 트리 갱신용). */
  public void relevel(int newLevel) {
    this.orgLvl = newLevel;
  }

  /**
   * 조직 폐지.
   *
   * <p>행을 지우지 않고 유효 종료일을 닫는다 — 과거 발령·위촉 이력이 이 조직을 참조하기 때문이다(설계서 6.6).
   */
  public void close(LocalDate closeDate) {
    if (isClosed()) {
      throw new IllegalStateException("이미 폐지된 조직입니다. 조직코드=" + orgCd);
    }
    if (closeDate.isBefore(validFromDt)) {
      throw new IllegalArgumentException("폐지일이 조직 개설일보다 앞설 수 없습니다.");
    }
    this.validToDt = closeDate;
    this.useYn = false;
  }

  /**
   * 이력에 남길 전체 스냅샷 (설계서 6.6 v1.2).
   *
   * <p>변경된 필드만이 아니라 전체 상태를 담는다 — 시점 조회가 이 스냅샷 하나로 복원되게 하기 위함이다.
   */
  public Map<String, Object> toSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("orgId", id);
    snapshot.put("orgCd", orgCd);
    snapshot.put("orgNm", orgNm);
    snapshot.put("orgTypeCd", orgTypeCd);
    snapshot.put("upOrgId", upOrgId);
    snapshot.put("orgLvl", orgLvl);
    snapshot.put("sortOrd", sortOrd);
    snapshot.put("validFromDt", validFromDt.toString());
    snapshot.put("validToDt", validToDt.toString());
    // 스냅샷의 표현은 DB 컬럼과 같은 'Y'/'N' 으로 둔다 — 시점 조회 SQL이 JSON_VALUE로 직접 비교한다.
    snapshot.put("useYn", useYn ? "Y" : "N");
    return snapshot;
  }

  public Long getId() {
    return id;
  }

  public String getOrgCd() {
    return orgCd;
  }

  public String getOrgNm() {
    return orgNm;
  }

  public String getOrgTypeCd() {
    return orgTypeCd;
  }

  public Long getUpOrgId() {
    return upOrgId;
  }

  public int getOrgLvl() {
    return orgLvl;
  }

  public int getSortOrd() {
    return sortOrd;
  }

  public LocalDate getValidFromDt() {
    return validFromDt;
  }

  public LocalDate getValidToDt() {
    return validToDt;
  }

  public boolean isUseYn() {
    return useYn;
  }
}
