package com.portfolio.insuhr.domain.emp;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 인사기록카드 — 가족 (설계서 6.5).
 *
 * <p>가족의 주민번호는 받지 않는다. 주민번호 원문은 {@code TB_PERSON}에만 두는 것이 설계서 5.2의 규칙이고, 가족은 인물로 등록되지 않으므로 담을 곳이 없는
 * 것이 맞다.
 */
@Entity
@Table(name = "TB_EMP_FAMILY")
public class EmpFamily extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "FAMILY_ID")
  private Long id;

  @Column(name = "EMP_ID", nullable = false)
  private Long empId;

  @Column(name = "RELATION_CD", nullable = false, length = 30)
  private String relationCd;

  @Column(name = "FAMILY_NM", nullable = false, length = 50)
  private String familyNm;

  @Column(name = "BIRTH_DT")
  private LocalDate birthDt;

  @Convert(converter = YnConverter.class)
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "COHABIT_YN", nullable = false, length = 1)
  private boolean cohabitYn;

  protected EmpFamily() {}

  public EmpFamily(
      Long empId, String relationCd, String familyNm, LocalDate birthDt, boolean cohabitYn) {
    this.empId = empId;
    this.relationCd = relationCd;
    this.familyNm = familyNm;
    this.birthDt = birthDt;
    this.cohabitYn = cohabitYn;
  }

  public void update(String relationCd, String familyNm, LocalDate birthDt, boolean cohabitYn) {
    this.relationCd = relationCd;
    this.familyNm = familyNm;
    this.birthDt = birthDt;
    this.cohabitYn = cohabitYn;
  }

  public Long getId() {
    return id;
  }

  public Long getEmpId() {
    return empId;
  }

  public String getRelationCd() {
    return relationCd;
  }

  public String getFamilyNm() {
    return familyNm;
  }

  public LocalDate getBirthDt() {
    return birthDt;
  }

  public boolean isCohabitYn() {
    return cohabitYn;
  }
}
