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
 * Refresh 토큰 (설계서 10.1 "Refresh(14일, DB 저장·회전)").
 *
 * <p>토큰 원문이 아니라 SHA-256 해시를 저장한다 — DB가 유출돼도 그 자체로는 재사용할 수 없어야 한다.
 *
 * <p>회전(rotation): refresh 할 때마다 기존 토큰을 폐기하고 새로 발급한다. 폐기된 토큰이 다시 쓰이면 탈취를 의심할 근거가 된다.
 */
@Entity
@Table(name = "TB_AUTH_REFRESH_TOKEN")
public class RefreshToken extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "TOKEN_ID")
  private Long id;

  @Column(name = "USER_ID", nullable = false)
  private Long userId;

  @Column(name = "TOKEN_HASH", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "ISSUED_AT", nullable = false)
  private Instant issuedAt;

  @Column(name = "EXPIRES_AT", nullable = false)
  private Instant expiresAt;

  @Column(name = "REVOKED_AT")
  private Instant revokedAt;

  protected RefreshToken() {}

  private RefreshToken(Long userId, String tokenHash, Instant expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.issuedAt = Instant.now();
    this.expiresAt = expiresAt;
  }

  public static RefreshToken issue(Long userId, String tokenHash, Instant expiresAt) {
    return new RefreshToken(userId, tokenHash, expiresAt);
  }

  /** 지금 사용할 수 있는 토큰인가. 만료됐거나 이미 폐기됐으면 안 된다. */
  public boolean isUsableAt(Instant at) {
    return revokedAt == null && at.isBefore(expiresAt);
  }

  /** 회전·로그아웃으로 폐기. */
  public void revoke() {
    if (revokedAt == null) {
      this.revokedAt = Instant.now();
    }
  }

  public Long getId() {
    return id;
  }

  public Long getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }
}
