package com.portfolio.insuhr.api.sync;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 연계 Pull API (설계서 7.2 IFC, 9.4). 시스템 계정 전용.
 *
 * <p>커서 재개형 변경분 조회. 워터마크 지연으로 시퀀스 갭 함정을 막는다(9.4). 사외로 데이터가 나가는 경로라 {@code sync.read} 권한을
 * 요구한다(10.2).
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

  private final SyncService syncService;

  public SyncController(SyncService syncService) {
    this.syncService = syncService;
  }

  @GetMapping("/changes")
  @PreAuthorize("hasAuthority('sync.read')")
  public ApiResponse<SyncService.SyncChangesResponse> changes(
      @RequestParam(required = false) String aggType,
      @RequestParam(required = false, defaultValue = "0") long cursor,
      @RequestParam(required = false, defaultValue = "500") int size) {
    return ApiResponse.ok(syncService.changes(aggType, cursor, size), TraceIdProvider.current());
  }
}
