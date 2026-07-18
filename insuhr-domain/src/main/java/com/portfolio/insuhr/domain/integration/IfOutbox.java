package com.portfolio.insuhr.domain.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 트랜잭셔널 아웃박스 행 (설계서 9.2).
 *
 * <p>기준정보 변경과 <b>같은 트랜잭션</b>에서 {@code READY}로 INSERT된다 — 업무 변경이 커밋되면 이벤트도 반드시 남고, 롤백되면 함께
 * 사라진다(유실·유령 이벤트 없음). relay(Phase 6)가 READY를 집어 전송하고 {@code SENT}/{@code FAILED}로 바꾼다.
 *
 * <p>감사컬럼(BaseEntity)을 상속하지 않는다 — 이벤트 테이블이라 자체 {@code OCCURRED_AT}/{@code PUBLISHED_AT}로 충분하고,
 * relay가 상태를 바꿀 때 감사 주체(SecurityContext)가 없다.
 */
@Entity
@Table(name = "TB_IF_OUTBOX")
public class IfOutbox {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "EVENT_ID")
  private Long id;

  @Column(name = "EVENT_UUID", nullable = false, length = 36)
  private String eventUuid;

  @Column(name = "AGG_TYPE", nullable = false, length = 30)
  private String aggType;

  @Column(name = "AGG_ID", nullable = false)
  private Long aggId;

  @Column(name = "EVENT_TYPE", nullable = false, length = 60)
  private String eventType;

  @Lob
  @Column(name = "PAYLOAD", nullable = false)
  private String payload;

  @Column(name = "STATUS_CD", nullable = false, length = 10)
  private String statusCd;

  @Column(name = "RETRY_CNT", nullable = false)
  private int retryCnt;

  @Column(name = "OCCURRED_AT", nullable = false)
  private Instant occurredAt;

  @Column(name = "PUBLISHED_AT")
  private Instant publishedAt;

  protected IfOutbox() {}

  private IfOutbox(
      String eventUuid,
      String aggType,
      Long aggId,
      String eventType,
      String payload,
      Instant occurredAt) {
    this.eventUuid = eventUuid;
    this.aggType = aggType;
    this.aggId = aggId;
    this.eventType = eventType;
    this.payload = payload;
    this.statusCd = OutboxStatus.READY.name();
    this.retryCnt = 0;
    this.occurredAt = occurredAt;
  }

  public static IfOutbox ready(
      String eventUuid,
      String aggType,
      Long aggId,
      String eventType,
      String payload,
      Instant occurredAt) {
    return new IfOutbox(eventUuid, aggType, aggId, eventType, payload, occurredAt);
  }

  /** 전송 성공. */
  public void markSent(Instant publishedAt) {
    this.statusCd = OutboxStatus.SENT.name();
    this.publishedAt = publishedAt;
  }

  /** 전송 실패 — 재시도 카운트 증가. 한도 초과 판정은 서비스가 한다. */
  public void markRetryFailed() {
    this.retryCnt++;
  }

  /** 재시도 한도 초과 — 더 이상 자동 재시도하지 않는다(수동 재전송 대상). */
  public void markFailed() {
    this.statusCd = OutboxStatus.FAILED.name();
  }

  /** 수동 재전송을 위해 다시 READY로 되돌린다. */
  public void requeue() {
    this.statusCd = OutboxStatus.READY.name();
  }

  public Long getId() {
    return id;
  }

  public String getEventUuid() {
    return eventUuid;
  }

  public String getAggType() {
    return aggType;
  }

  public Long getAggId() {
    return aggId;
  }

  public String getEventType() {
    return eventType;
  }

  public String getPayload() {
    return payload;
  }

  public OutboxStatus getStatus() {
    return OutboxStatus.valueOf(statusCd);
  }

  public int getRetryCnt() {
    return retryCnt;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }
}
