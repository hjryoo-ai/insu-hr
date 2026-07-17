package com.portfolio.insuhr.domain.support;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 업무 테이블이 갖는 감사 컬럼 (설계서 6.2).
 *
 * <pre>
 * CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
 * UPDATED_AT TIMESTAMP,                               UPDATED_BY VARCHAR2(50)
 * </pre>
 *
 * <p>이 클래스가 채우는 4개 컬럼은 "누가 언제 이 행을 건드렸나"까지만 담는다. 무엇이 어떻게 바뀌었는지(before/after JSON)는 별도의 {@code
 * TB_AUDIT_LOG}가 AOP로 남긴다(설계서 6.6).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

  @CreatedDate
  @Column(name = "CREATED_AT", nullable = false, updatable = false)
  private Instant createdAt;

  @CreatedBy
  @Column(name = "CREATED_BY", nullable = false, updatable = false, length = 50)
  private String createdBy;

  @LastModifiedDate
  @Column(name = "UPDATED_AT")
  private Instant updatedAt;

  @LastModifiedBy
  @Column(name = "UPDATED_BY", length = 50)
  private String updatedBy;

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }
}
