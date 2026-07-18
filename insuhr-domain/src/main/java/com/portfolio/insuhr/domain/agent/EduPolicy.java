package com.portfolio.insuhr.domain.agent;

import com.portfolio.insuhr.domain.support.BaseEntity;
import com.portfolio.insuhr.domain.support.YnConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 교육 정책 (설계서 6.5). 교육유형별 최소 이수시간·필수여부의 참조 데이터.
 *
 * <p>교육 등록 시 이수시간 검증(MIN_HOURS)에 쓴다. 보수교육 주기는 정책값 {@code CONT_EDU_CYCLE_MONTHS}가 권위 원천이고(설계서 5.4
 * v1.6), 이 테이블의 {@code CYCLE_MONTHS}는 참조·보고용으로 정합만 맞춘다.
 */
@Entity
@Table(name = "TB_EDU_POLICY")
public class EduPolicy extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "EDU_POLICY_ID")
  private Long id;

  @Column(name = "EDU_TYPE_CD", nullable = false, length = 30)
  private String eduTypeCd;

  @Column(name = "CYCLE_MONTHS")
  private Integer cycleMonths;

  @Column(name = "MIN_HOURS", nullable = false, precision = 5, scale = 1)
  private BigDecimal minHours;

  // _YN은 CHAR(1)이라 @JdbcTypeCode(CHAR)이 없으면 validate가 VARCHAR2를 기대해 기동을 막는다(설계서 3.0, YnConverter).
  @Convert(converter = YnConverter.class)
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "MANDATORY_YN", nullable = false, length = 1)
  private boolean mandatory;

  @Column(name = "APPLY_CHANNEL_CD", length = 30)
  private String applyChannelCd;

  @Column(name = "VALID_FROM_DT", nullable = false)
  private LocalDate validFromDt;

  protected EduPolicy() {}

  public EduType getType() {
    return EduType.valueOf(eduTypeCd);
  }

  public Integer getCycleMonths() {
    return cycleMonths;
  }

  public BigDecimal getMinHours() {
    return minHours;
  }

  public boolean isMandatory() {
    return mandatory;
  }

  public Long getId() {
    return id;
  }
}
