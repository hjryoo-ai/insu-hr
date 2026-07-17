package com.portfolio.insuhr.api.policy;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정책값 조회 API (설계서 7.2 공통/관리).
 *
 * <p>권한 검사는 URL 패턴이 아니라 {@code @PreAuthorize}로 한다(설계서 10.1). 같은 경로에서 조회/수정이 다른 권한을 요구하기 때문이다.
 */
@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

  private final PolicyConfigService policyConfigService;

  public PolicyController(PolicyConfigService policyConfigService) {
    this.policyConfigService = policyConfigService;
  }

  @GetMapping("/{policyKey}")
  @PreAuthorize("hasAuthority('policy.read')")
  public ApiResponse<PolicyResponse> get(@PathVariable PolicyKey policyKey) {
    return ApiResponse.ok(
        new PolicyResponse(policyKey.name(), policyConfigService.getString(policyKey)),
        TraceIdProvider.current());
  }

  public record PolicyResponse(String key, String value) {}
}
