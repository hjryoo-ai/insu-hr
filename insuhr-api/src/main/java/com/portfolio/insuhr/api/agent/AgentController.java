package com.portfolio.insuhr.api.agent;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import com.portfolio.insuhr.domain.agent.Agent;
import com.portfolio.insuhr.domain.agent.Association;
import com.portfolio.insuhr.domain.agent.Channel;
import com.portfolio.insuhr.domain.agent.TermReason;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 설계사 API (설계서 7.2 AGT). Phase 4 범위: 후보등록·위촉·정지·해제·해촉·재위촉·이동·계보.
 *
 * <p>자격/교육/보증/제재/불완전판매/교차모집은 Phase 5, 계약 상세 관리·계좌 복호화도 Phase 5다.
 */
@RestController
@RequestMapping("/api/v1")
public class AgentController {

  private final AgentService agentService;

  public AgentController(AgentService agentService) {
    this.agentService = agentService;
  }

  /** 후보 등록 (설계서 7.2 {@code POST /agents/candidates}). */
  @PostMapping("/agents/candidates")
  @PreAuthorize("hasAuthority('agent.write')")
  public ApiResponse<RegisterResponse> registerCandidate(
      @Valid @RequestBody RegisterRequest request) {
    AgentService.RegisterResult result =
        agentService.registerCandidate(
            new NewPerson(
                request.personNm(),
                request.rrn(),
                request.birthDt(),
                request.gender(),
                request.mobile(),
                request.email(),
                request.nationalityCd()),
            new AgentService.RegisterCommand(
                request.channel(), request.orgId(), request.recruiterAgentCd()));
    return ApiResponse.ok(
        new RegisterResponse(result.agentId(), result.agentCd(), result.personId()),
        TraceIdProvider.current());
  }

  /** 설계사 상세 (설계서 7.2 {@code GET /agents/{id}}). */
  @GetMapping("/agents/{agentId}")
  @PreAuthorize("hasAuthority('agent.read')")
  public ApiResponse<AgentResponse> get(@PathVariable Long agentId) {
    return ApiResponse.ok(AgentResponse.from(agentService.get(agentId)), TraceIdProvider.current());
  }

  /** 위촉 실행 — 요건검증 후 PENDING_ASSOC (설계서 7.2 {@code POST /agents/{id}/appoint}). 미충족 시 422. */
  @PostMapping("/agents/{agentId}/appoint")
  @PreAuthorize("hasAuthority('agent.appoint')")
  public ApiResponse<Void> appoint(
      @PathVariable Long agentId, @Valid @RequestBody AppointRequest request) {
    agentService.appoint(
        agentId,
        request.appointDt(),
        new AgentService.ContractCommand(
            request.contractTypeCd(),
            request.commRuleVer(),
            request.bankCd(),
            request.account(),
            request.accountHolderNm()));
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  /** 협회 등록번호 입력 → ACTIVE (설계서 7.2 {@code POST /agents/{id}/assoc-registrations}). */
  @PostMapping("/agents/{agentId}/assoc-registrations")
  @PreAuthorize("hasAuthority('agent.write')")
  public ApiResponse<Void> registerAssociation(
      @PathVariable Long agentId, @Valid @RequestBody AssocRegRequest request) {
    agentService.registerAssociation(
        agentId, request.regDt(), request.assoc(), request.assocRegNo());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  /** 모집정지 (설계서 7.2 {@code POST /agents/{id}/suspend}). */
  @PostMapping("/agents/{agentId}/suspend")
  @PreAuthorize("hasAuthority('agent.write')")
  public ApiResponse<Void> suspend(
      @PathVariable Long agentId, @Valid @RequestBody SuspendRequest request) {
    agentService.suspend(agentId, request.eventDt(), request.rsnCd(), request.rsnTxt());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  /** 정지해제 (설계서 7.2 {@code POST /agents/{id}/resume}). */
  @PostMapping("/agents/{agentId}/resume")
  @PreAuthorize("hasAuthority('agent.write')")
  public ApiResponse<Void> resume(
      @PathVariable Long agentId, @Valid @RequestBody ResumeRequest request) {
    agentService.resume(agentId, request.eventDt(), request.rsnTxt());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  /** 해촉 (설계서 7.2 {@code POST /agents/{id}/terminate}). */
  @PostMapping("/agents/{agentId}/terminate")
  @PreAuthorize("hasAuthority('agent.terminate')")
  public ApiResponse<Void> terminate(
      @PathVariable Long agentId, @Valid @RequestBody TerminateRequest request) {
    agentService.terminate(agentId, request.terminateDt(), request.reason(), request.rsnTxt());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  /** 재위촉 (설계서 7.2 {@code POST /agents/{id}/reappoint}). 냉각기간·금지사유는 도메인이 검사한다. */
  @PostMapping("/agents/{agentId}/reappoint")
  @PreAuthorize("hasAuthority('agent.write')")
  public ApiResponse<Void> reappoint(
      @PathVariable Long agentId, @RequestBody(required = false) ReappointRequest request) {
    agentService.reappoint(agentId, request == null ? null : request.rsnTxt());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  /** 소속 이동 (설계서 7.2 {@code PUT /agents/{id}/transfer}). */
  @PutMapping("/agents/{agentId}/transfer")
  @PreAuthorize("hasAuthority('agent.write')")
  public ApiResponse<Void> transfer(
      @PathVariable Long agentId, @Valid @RequestBody TransferRequest request) {
    agentService.transfer(agentId, request.orgId(), request.eventDt(), request.rsnTxt());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  /** 도입 계보 트리 (설계서 7.2 {@code GET /agents/{id}/genealogy}). */
  @GetMapping("/agents/{agentId}/genealogy")
  @PreAuthorize("hasAuthority('agent.read')")
  public ApiResponse<AgentService.GenealogyNode> genealogy(@PathVariable Long agentId) {
    return ApiResponse.ok(agentService.genealogy(agentId), TraceIdProvider.current());
  }

  public record RegisterRequest(
      @NotBlank @Size(max = 100) String personNm,
      @NotBlank @Pattern(regexp = "\\d{6}-?\\d{7}", message = "주민등록번호 형식이 올바르지 않습니다.") String rrn,
      @NotNull LocalDate birthDt,
      @NotNull Gender gender,
      String mobile,
      @Size(max = 100) String email,
      @Size(max = 10) String nationalityCd,
      @NotNull Channel channel,
      @NotNull Long orgId,
      @Size(max = 10) String recruiterAgentCd) {}

  public record RegisterResponse(Long agentId, String agentCd, Long personId) {}

  public record AppointRequest(
      @NotNull LocalDate appointDt,
      @NotBlank @Size(max = 30) String contractTypeCd,
      @Size(max = 30) String commRuleVer,
      @Size(max = 30) String bankCd,
      String account,
      @Size(max = 100) String accountHolderNm) {}

  public record AssocRegRequest(
      @NotNull LocalDate regDt,
      @NotNull Association assoc,
      @NotBlank @Size(max = 30) String assocRegNo) {}

  public record SuspendRequest(
      @NotNull LocalDate eventDt,
      @NotBlank @Size(max = 30) String rsnCd,
      @Size(max = 400) String rsnTxt) {}

  public record ResumeRequest(@NotNull LocalDate eventDt, @Size(max = 400) String rsnTxt) {}

  public record TerminateRequest(
      @NotNull LocalDate terminateDt, @NotNull TermReason reason, @Size(max = 400) String rsnTxt) {}

  public record ReappointRequest(@Size(max = 400) String rsnTxt) {}

  public record TransferRequest(
      @NotNull Long orgId, @NotNull LocalDate eventDt, @Size(max = 400) String rsnTxt) {}

  public record AgentResponse(
      Long agentId,
      String agentCd,
      Long personId,
      String channelCd,
      Long orgId,
      String statusCd,
      LocalDate firstAppointDt,
      LocalDate lastAppointDt,
      LocalDate terminateDt,
      String terminateRsnCd,
      Long recruiterAgentId,
      boolean recruitEligible) {

    static AgentResponse from(Agent a) {
      return new AgentResponse(
          a.getId(),
          a.getAgentCd(),
          a.getPersonId(),
          a.getChannel().name(),
          a.getOrgId(),
          a.getStatus().name(),
          a.getFirstAppointDt(),
          a.getLastAppointDt(),
          a.getTerminateDt(),
          a.getTerminateRsnCd(),
          a.getRecruiterAgentId(),
          a.isRecruitEligible());
    }
  }
}
