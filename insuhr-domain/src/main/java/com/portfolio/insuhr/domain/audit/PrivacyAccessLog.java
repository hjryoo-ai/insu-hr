package com.portfolio.insuhr.domain.audit;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 개인정보 조회 접근로그 (설계서 10.2).
 *
 * <p><b>이 기록은 열람과 같은 트랜잭션에서 남는다 — 기록 없으면 열람 없음</b>(설계서 10.1.1). 로그 INSERT가 실패하면 복호화 응답도 실패해야 한다.
 * 로그인 실패 카운트가 롤백에서 살아남아야 하는 것과 정반대 방향이므로 혼동하지 말 것.
 */
@Entity
@Table(name = "TB_PRIVACY_ACCESS_LOG")
public class PrivacyAccessLog extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "LOG_ID")
  private Long id;

  @Column(name = "USER_ID", nullable = false)
  private Long userId;

  @Column(name = "TARGET_PERSON_ID", nullable = false)
  private Long targetPersonId;

  @Column(name = "ACCESS_TYPE_CD", nullable = false, length = 10)
  private String accessTypeCd;

  @Column(name = "ACCESS_AT", nullable = false)
  private Instant accessAt;

  @Column(name = "MENU_OR_API", nullable = false, length = 200)
  private String menuOrApi;

  @Column(name = "CLIENT_IP", length = 45)
  private String clientIp;

  @Column(name = "PURPOSE_TXT", length = 400)
  private String purposeTxt;

  protected PrivacyAccessLog() {}

  private PrivacyAccessLog(
      Long userId,
      Long targetPersonId,
      PrivacyAccessType accessType,
      String menuOrApi,
      String clientIp,
      String purposeTxt) {
    this.userId = userId;
    this.targetPersonId = targetPersonId;
    this.accessTypeCd = accessType.name();
    this.accessAt = Instant.now();
    this.menuOrApi = menuOrApi;
    this.clientIp = clientIp;
    this.purposeTxt = purposeTxt;
  }

  public static PrivacyAccessLog of(
      Long userId,
      Long targetPersonId,
      PrivacyAccessType accessType,
      String menuOrApi,
      String clientIp,
      String purposeTxt) {
    return new PrivacyAccessLog(
        userId, targetPersonId, accessType, menuOrApi, clientIp, purposeTxt);
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public Long getTargetPersonId() {
    return targetPersonId;
  }

  public String getAccessTypeCd() {
    return accessTypeCd;
  }

  public Instant getAccessAt() {
    return accessAt;
  }

  public String getMenuOrApi() {
    return menuOrApi;
  }

  public String getClientIp() {
    return clientIp;
  }

  public String getPurposeTxt() {
    return purposeTxt;
  }
}
