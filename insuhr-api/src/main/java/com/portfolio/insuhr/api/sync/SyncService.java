package com.portfolio.insuhr.api.sync;

import com.portfolio.insuhr.domain.integration.SyncChangeQueryDao;
import com.portfolio.insuhr.domain.integration.SyncChangeQueryDao.ChangeRow;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 변경분 Pull (설계서 9.4 {@code GET /sync/changes}).
 *
 * <p>워터마크 지연 초는 정책값 {@code SYNC_WATERMARK_SECONDS}에서 읽는다(하드코딩 금지). snapshot은 CLOB JSON을 {@link
 * JsonNode}로 파싱해 응답에 그대로 실어(이중 인코딩 방지) 수신측이 upsert에 쓴다.
 */
@Service
public class SyncService {

  private static final int MAX_PAGE = 1000;

  private final SyncChangeQueryDao changeQueryDao;
  private final PolicyConfigService policyConfig;
  private final ObjectMapper objectMapper;

  public SyncService(
      SyncChangeQueryDao changeQueryDao,
      PolicyConfigService policyConfig,
      ObjectMapper objectMapper) {
    this.changeQueryDao = changeQueryDao;
    this.policyConfig = policyConfig;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public SyncChangesResponse changes(String aggType, long cursor, int size) {
    int watermark = policyConfig.getInt(PolicyKey.SYNC_WATERMARK_SECONDS);
    int pageSize = Math.min(Math.max(size, 1), MAX_PAGE);

    List<ChangeRow> rows = changeQueryDao.findChanges(aggType, cursor, pageSize, watermark);
    List<SyncChange> items = rows.stream().map(this::toChange).toList();

    long nextCursor = rows.isEmpty() ? cursor : rows.get(rows.size() - 1).seqNo();
    boolean hasMore = rows.size() == pageSize;
    return new SyncChangesResponse(items, nextCursor, hasMore);
  }

  private SyncChange toChange(ChangeRow row) {
    JsonNode snapshot = objectMapper.readTree(row.snapshotJson());
    return new SyncChange(
        row.seqNo(), row.aggType(), row.aggId(), row.changeType(), row.changedAt(), snapshot);
  }

  /** 변경분 한 건. snapshot은 변경 후 전체 상태. */
  public record SyncChange(
      long seqNo,
      String aggType,
      long aggId,
      String changeType,
      String changedAt,
      JsonNode snapshot) {}

  /** 커서 재개형 응답. 수신측은 nextCursor(마지막 seqNo)만 저장하면 재수신된다. */
  public record SyncChangesResponse(List<SyncChange> items, long nextCursor, boolean hasMore) {}
}
