package com.portfolio.insuhr.api.subscriber;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Outbox 운영 API (설계서 7.2 {@code /admin/outbox}). 연계 운영({@code integration.admin}) 전용. */
@RestController
@RequestMapping("/api/v1/admin/outbox")
public class OutboxAdminController {

  private final OutboxAdminService outboxAdminService;

  public OutboxAdminController(OutboxAdminService outboxAdminService) {
    this.outboxAdminService = outboxAdminService;
  }

  @PostMapping("/{eventId}/resend")
  @PreAuthorize("hasAuthority('integration.admin')")
  public ApiResponse<ResendResponse> resend(@PathVariable Long eventId) {
    int requeued = outboxAdminService.resend(eventId);
    return ApiResponse.ok(new ResendResponse(eventId, requeued), TraceIdProvider.current());
  }

  /** 재전송 결과 — 되돌린 전달 레코드 수. */
  public record ResendResponse(Long eventId, int requeuedDeliveries) {}
}
