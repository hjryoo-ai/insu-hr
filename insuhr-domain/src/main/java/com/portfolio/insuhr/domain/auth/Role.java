package com.portfolio.insuhr.domain.auth;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 역할 (설계서 10.1). */
@Entity
@Table(name = "TB_ROLE")
public class Role extends BaseEntity {

  @Id
  @Column(name = "ROLE_CD", length = 30)
  private String roleCd;

  @Column(name = "ROLE_NM", nullable = false, length = 100)
  private String roleNm;

  @Column(name = "DESC_TXT", length = 400)
  private String descTxt;

  protected Role() {}

  public String getRoleCd() {
    return roleCd;
  }

  public String getRoleNm() {
    return roleNm;
  }

  public String getDescTxt() {
    return descTxt;
  }
}
