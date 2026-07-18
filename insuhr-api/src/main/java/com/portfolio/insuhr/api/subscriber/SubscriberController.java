package com.portfolio.insuhr.api.subscriber;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import com.portfolio.insuhr.domain.integration.IfSubscriber;
import com.portfolio.insuhr.domain.integration.IfSubscriberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구독 시스템 관리 API (설계서 7.2 {@code /admin/subscribers}). 연계 운영({@code integration.admin}) 전용.
 *
 * <p>시크릿 평문은 생성 시에만 받아 즉시 AES 암호화되며 응답·조회에는 절대 실리지 않는다(10.3). 비활성화는 미전송 전달분을 SKIPPED로 종결해 요약을
 * 수렴시킨다(도메인 서비스가 규칙 담당, 9.2 v1.7).
 */
@RestController
@RequestMapping("/api/v1/admin/subscribers")
public class SubscriberController {

  private final IfSubscriberService subscriberService;

  public SubscriberController(IfSubscriberService subscriberService) {
    this.subscriberService = subscriberService;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('integration.admin')")
  public ApiResponse<SubscriberResponse> create(
      @Valid @RequestBody CreateSubscriberRequest request) {
    IfSubscriber created =
        subscriberService.create(
            request.systemCd(),
            request.systemNm(),
            request.deliveryTypeCd(),
            request.endpointUrl(),
            request.secret(),
            request.topicFilterJson());
    return ApiResponse.ok(SubscriberResponse.from(created), TraceIdProvider.current());
  }

  @GetMapping
  @PreAuthorize("hasAuthority('integration.admin')")
  public ApiResponse<List<SubscriberResponse>> list() {
    List<SubscriberResponse> items =
        subscriberService.list().stream().map(SubscriberResponse::from).toList();
    return ApiResponse.ok(items, TraceIdProvider.current());
  }

  @PutMapping("/{subscriberId}/deactivate")
  @PreAuthorize("hasAuthority('integration.admin')")
  public ApiResponse<Void> deactivate(@PathVariable Long subscriberId) {
    subscriberService.deactivate(subscriberId);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  @PutMapping("/{subscriberId}/activate")
  @PreAuthorize("hasAuthority('integration.admin')")
  public ApiResponse<Void> activate(@PathVariable Long subscriberId) {
    subscriberService.activate(subscriberId);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  /** 구독자 생성 요청. secret은 서명 시크릿 평문 — 저장 시 즉시 암호화된다. */
  public record CreateSubscriberRequest(
      @NotBlank String systemCd,
      @NotBlank String systemNm,
      @NotBlank String deliveryTypeCd,
      String endpointUrl,
      String secret,
      String topicFilterJson) {}

  /** 구독자 응답 — 시크릿은 절대 포함하지 않는다. */
  public record SubscriberResponse(
      Long id,
      String systemCd,
      String systemNm,
      String deliveryTypeCd,
      String endpointUrl,
      boolean active) {

    public static SubscriberResponse from(IfSubscriber subscriber) {
      return new SubscriberResponse(
          subscriber.getId(),
          subscriber.getSystemCd(),
          subscriber.getSystemNm(),
          subscriber.getDeliveryTypeCd(),
          subscriber.getEndpointUrl(),
          subscriber.isActive());
    }
  }
}
