package com.portfolio.insuhr.domain.auth;

import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 계정 (설계서 6.5, 10.1).
 *
 * <p>로그인 성공/실패에 따른 잠금 판정은 이 엔티티가 스스로 한다 — 상태 전이 규칙을 애플리케이션 서비스에 흘리지 않기 위함(설계서 4.3).
 */
@Entity
@Table(name = "TB_USER")
public class UserAccount extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "USER_ID")
  private Long id;

  @Column(name = "LOGIN_ID", nullable = false, length = 50)
  private String loginId;

  @Column(name = "PWD_HASH", length = 100)
  private String passwordHash;

  @Column(name = "PERSON_ID")
  private Long personId;

  @Column(name = "USER_TYPE_CD", nullable = false, length = 10)
  private String userTypeCd;

  @Column(name = "STATUS_CD", nullable = false, length = 10)
  private String statusCd;

  @Column(name = "LOGIN_FAIL_CNT", nullable = false)
  private int loginFailCnt;

  @Column(name = "LOCKED_AT")
  private Instant lockedAt;

  @Column(name = "LAST_LOGIN_AT")
  private Instant lastLoginAt;

  @Column(name = "PWD_CHANGED_AT")
  private Instant passwordChangedAt;

  protected UserAccount() {}

  private UserAccount(String loginId, String passwordHash, UserType userType, Long personId) {
    this.loginId = loginId;
    this.passwordHash = passwordHash;
    this.userTypeCd = userType.name();
    this.personId = personId;
    this.statusCd = UserStatus.ACTIVE.name();
    this.loginFailCnt = 0;
    this.passwordChangedAt = Instant.now();
  }

  /** 사람 계정. 비밀번호는 이미 BCrypt로 인코딩된 값을 받는다. */
  public static UserAccount ofHuman(String loginId, String encodedPassword, Long personId) {
    if (encodedPassword == null || encodedPassword.isBlank()) {
      throw new IllegalArgumentException("사람 계정에는 비밀번호가 필요합니다.");
    }
    return new UserAccount(loginId, encodedPassword, UserType.HUMAN, personId);
  }

  /** 연계용 기계 계정. 비밀번호 로그인을 하지 않는다 (설계서 7.2 /auth/system-token). */
  public static UserAccount ofSystem(String loginId) {
    return new UserAccount(loginId, null, UserType.SYSTEM, null);
  }

  /** 로그인 가능한 상태인가. 잠금·사용중지 계정은 비밀번호가 맞아도 로그인시키지 않는다. */
  public boolean isLoginAllowed() {
    return UserStatus.ACTIVE.name().equals(statusCd);
  }

  public boolean isLocked() {
    return UserStatus.LOCKED.name().equals(statusCd);
  }

  public boolean isSystemAccount() {
    return UserType.SYSTEM.name().equals(userTypeCd);
  }

  /**
   * 로그인 실패를 기록하고, 임계값에 도달하면 스스로 잠근다 (설계서 10.1).
   *
   * @param lockThreshold 정책값 LOGIN_FAIL_LOCK_CNT. 하드코딩하지 않고 주입받는다(설계서 13.1)
   */
  public void recordLoginFailure(int lockThreshold) {
    this.loginFailCnt++;
    if (this.loginFailCnt >= lockThreshold) {
      this.statusCd = UserStatus.LOCKED.name();
      this.lockedAt = Instant.now();
    }
  }

  /** 로그인 성공. 실패 카운트를 초기화한다. */
  public void recordLoginSuccess() {
    this.loginFailCnt = 0;
    this.lastLoginAt = Instant.now();
  }

  /** 잠금 해제 (관리자 조치). */
  public void unlock() {
    this.statusCd = UserStatus.ACTIVE.name();
    this.lockedAt = null;
    this.loginFailCnt = 0;
  }

  public Long getId() {
    return id;
  }

  public String getLoginId() {
    return loginId;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Long getPersonId() {
    return personId;
  }

  public String getUserTypeCd() {
    return userTypeCd;
  }

  public String getStatusCd() {
    return statusCd;
  }

  public int getLoginFailCnt() {
    return loginFailCnt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public Instant getLockedAt() {
    return lockedAt;
  }

  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }
}
