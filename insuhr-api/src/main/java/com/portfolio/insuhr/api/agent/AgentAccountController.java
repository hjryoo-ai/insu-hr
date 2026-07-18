package com.portfolio.insuhr.api.agent;

import com.portfolio.insuhr.api.security.AuthenticatedUser;
import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지급계좌 복호화 API (설계서 7.2 백로그, Phase 8).
 *
 * <p>주민번호 복호화({@code POST /persons/{id}/rrn})와 <b>같은 규칙</b>: 사유를 <b>본문</b>에 담는 POST(쿼리스트링으로 새지 않게),
 * {@code agent.account.decrypt} 권한, 접근로그 필수. 응답은 원문 + 즉석 마스킹을 함께 준다.
 */
@RestController
@RequestMapping("/api/v1")
public class AgentAccountController {

  private final AgentAccountService agentAccountService;

  public AgentAccountController(AgentAccountService agentAccountService) {
    this.agentAccountService = agentAccountService;
  }

  @PostMapping("/agents/{agentId}/account")
  @PreAuthorize("hasAuthority('agent.account.decrypt')")
  public ApiResponse<AccountResponse> decryptAccount(
      @PathVariable Long agentId,
      @Valid @RequestBody DecryptAccountRequest request,
      @AuthenticationPrincipal AuthenticatedUser actor,
      HttpServletRequest httpRequest) {

    AgentAccountService.AccountDecryptResult result =
        agentAccountService.decryptAccount(
            agentId,
            actor.userId(),
            request.purpose(),
            "POST /api/v1/agents/{agentId}/account",
            clientIpOf(httpRequest));

    return ApiResponse.ok(
        new AccountResponse(
            agentId,
            result.account(),
            result.accountMasked(),
            result.bankCd(),
            result.accountHolderNm()),
        TraceIdProvider.current());
  }

  /** 프록시 뒤에서도 원 클라이언트를 남기려 X-Forwarded-For를 먼저 본다. */
  private String clientIpOf(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  public record DecryptAccountRequest(
      @NotBlank(message = "개인정보 열람 사유를 입력해야 합니다.") @Size(max = 400) String purpose) {}

  /** 원문 계좌 + 즉석 마스킹(수신 UI 재마스킹 방지) + 참고용 은행/예금주. */
  public record AccountResponse(
      Long agentId, String account, String accountMasked, String bankCd, String accountHolderNm) {}
}
