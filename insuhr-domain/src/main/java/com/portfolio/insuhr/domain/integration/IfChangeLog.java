package com.portfolio.insuhr.domain.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * 변경분 Pull용 변경 로그 (설계서 9.4).
 *
 * <p>{@code SEQ_NO}가 커서다 — 수신측은 마지막 처리 SEQ_NO만 저장하면 그 지점부터 재개할 수 있다. {@code SNAPSHOT_JSON}은 변경 후 전체
 * 상태(state-carried transfer)라 순서가 꼬여도 upsert가 안전하다.
 *
 * <p><b>{@code allocationSize=1}</b>: Hibernate 기본 pooled 최적화는 시퀀스를 50씩 미리 당겨 SEQ_NO에 큰 갭을 만든다.
 * 커서·워터마크 판정이 SEQ_NO 연속성에 의존하지는 않지만(단조 증가면 충분), 갭을 작게 유지해 커서 값이 실제 행 수에 가깝도록 1로 둔다.
 */
@Entity
@Table(name = "TB_IF_CHANGE_LOG")
public class IfChangeLog {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ifChangeLogSeq")
  @SequenceGenerator(
      name = "ifChangeLogSeq",
      sequenceName = "SEQ_IF_CHANGE_LOG",
      allocationSize = 1)
  @Column(name = "SEQ_NO")
  private Long seqNo;

  @Column(name = "AGG_TYPE", nullable = false, length = 30)
  private String aggType;

  @Column(name = "AGG_ID", nullable = false)
  private Long aggId;

  @Column(name = "CHANGE_TYPE_CD", nullable = false, length = 10)
  private String changeTypeCd;

  @Lob
  @Column(name = "SNAPSHOT_JSON", nullable = false)
  private String snapshotJson;

  protected IfChangeLog() {}

  private IfChangeLog(String aggType, Long aggId, String changeTypeCd, String snapshotJson) {
    this.aggType = aggType;
    this.aggId = aggId;
    this.changeTypeCd = changeTypeCd;
    this.snapshotJson = snapshotJson;
  }

  public static IfChangeLog of(
      String aggType, Long aggId, String changeTypeCd, String snapshotJson) {
    return new IfChangeLog(aggType, aggId, changeTypeCd, snapshotJson);
  }

  public Long getSeqNo() {
    return seqNo;
  }

  public String getAggType() {
    return aggType;
  }

  public Long getAggId() {
    return aggId;
  }

  public String getChangeTypeCd() {
    return changeTypeCd;
  }

  public String getSnapshotJson() {
    return snapshotJson;
  }
}
