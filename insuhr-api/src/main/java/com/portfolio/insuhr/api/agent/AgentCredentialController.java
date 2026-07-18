package com.portfolio.insuhr.api.agent;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import com.portfolio.insuhr.domain.agent.Association;
import com.portfolio.insuhr.domain.agent.EduType;
import com.portfolio.insuhr.domain.agent.GuaranteeStatus;
import com.portfolio.insuhr.domain.agent.LicenseStatus;
import com.portfolio.insuhr.domain.agent.LicenseType;
import com.portfolio.insuhr.domain.eligibility.EligibilityResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 설계사 자격·교육·보증·제재 API + 모집자격 판정 조회 (설계서 7.2 AGT).
 *
 * <p>쓰기는 전부 {@code AgentCredentialService}를 거쳐 reconciler로 흐른다 — 자격이 바뀌면 판정이 다시 돌고 필요하면 자동 전이한다.
 */
@RestController
@RequestMapping("/api/v1")
public class AgentCredentialController {

  private final AgentCredentialService credentialService;
  private final AgentService agentService;

  public AgentCredentialController(
      AgentCredentialService credentialService, AgentService agentService) {
    this.credentialService = credentialService;
    this.agentService = agentService;
  }

  /** 판매자격 등록 (설계서 7.2 {@code POST /agents/{id}/licenses}). */
  @PostMapping("/agents/{agentId}/licenses")
  @PreAuthorize("hasAuthority('agent.write')")
  public ApiResponse<IdResponse> registerLicense(
      @PathVariable Long agentId, @Valid @RequestBody LicenseRequest request) {
    Long id =
        credentialService.registerLicense(
            agentId,
            request.type(),
            request.licenseNo(),
            request.examPassDt(),
            request.regDt(),
            request.status() == null ? LicenseStatus.VALID : request.status());
    return ApiResponse.ok(new IdResponse(id), TraceIdProvider.current());
  }

  /** 교육 이수 등록 — 보수교육이면 NEXT_DUE_DT 자동계산 (설계서 7.2 {@code POST /agents/{id}/educations}). */
  @PostMapping("/agents/{agentId}/educations")
  @PreAuthorize("hasAuthority('agent.write')")
  public ApiResponse<IdResponse> registerEducation(
      @PathVariable Long agentId, @Valid @RequestBody EducationRequest request) {
    Long id =
        credentialService.registerEducation(
            agentId,
            request.type(),
            request.eduNm(),
            request.completeDt(),
            request.eduHours(),
            request.providerNm());
    return ApiResponse.ok(new IdResponse(id), TraceIdProvider.current());
  }

  /** 재정보증 등록/갱신 (설계서 7.2 {@code POST /agents/{id}/guarantees}). */
  @PostMapping("/agents/{agentId}/guarantees")
  @PreAuthorize("hasAuthority('agent.write')")
  public ApiResponse<IdResponse> registerGuarantee(
      @PathVariable Long agentId, @Valid @RequestBody GuaranteeRequest request) {
    Long id =
        credentialService.registerGuarantee(
            agentId,
            request.grntTypeCd(),
            request.grntAmt(),
            request.issuerNm(),
            request.policyNo(),
            request.startDt(),
            request.endDt(),
            request.status() == null ? GuaranteeStatus.ACTIVE : request.status());
    return ApiResponse.ok(new IdResponse(id), TraceIdProvider.current());
  }

  /** 협회 등록 (설계서 7.2 {@code POST /agents/{id}/assoc-registrations}의 종목 협회 등록분). */
  @PostMapping("/agents/{agentId}/association-registrations")
  @PreAuthorize("hasAuthority('agent.write')")
  public ApiResponse<IdResponse> registerAssoc(
      @PathVariable Long agentId, @Valid @RequestBody AssocRequest request) {
    Long id =
        credentialService.registerAssoc(
            agentId, request.assoc(), request.assocRegNo(), request.regDt());
    return ApiResponse.ok(new IdResponse(id), TraceIdProvider.current());
  }

  /** 제재 등록 (설계서 7.2 {@code POST /agents/{id}/sanctions}). RECRUIT_BLOCK=Y면 자동 SUSPENDED. */
  @PostMapping("/agents/{agentId}/sanctions")
  @PreAuthorize("hasAuthority('agent.sanction')")
  public ApiResponse<IdResponse> imposeSanction(
      @PathVariable Long agentId, @Valid @RequestBody SanctionRequest request) {
    Long id =
        credentialService.imposeSanction(
            agentId,
            request.sanctionOrgCd(),
            request.sanctionTypeCd(),
            request.startDt(),
            request.endDt(),
            request.rsnTxt(),
            request.recruitBlock());
    return ApiResponse.ok(new IdResponse(id), TraceIdProvider.current());
  }

  /** 모집자격 실시간 판정 (설계서 7.2 {@code GET /agents/{id}/eligibility}). */
  @GetMapping("/agents/{agentId}/eligibility")
  @PreAuthorize("hasAuthority('agent.read')")
  public ApiResponse<EligibilityResponse> eligibility(@PathVariable Long agentId) {
    return ApiResponse.ok(
        EligibilityResponse.from(agentService.eligibility(agentId)), TraceIdProvider.current());
  }

  public record IdResponse(Long id) {}

  public record LicenseRequest(
      @NotNull LicenseType type,
      @Size(max = 30) String licenseNo,
      LocalDate examPassDt,
      LocalDate regDt,
      LicenseStatus status) {}

  public record EducationRequest(
      @NotNull EduType type,
      @Size(max = 200) String eduNm,
      @NotNull LocalDate completeDt,
      @NotNull @Positive BigDecimal eduHours,
      @Size(max = 100) String providerNm) {}

  public record GuaranteeRequest(
      @NotNull @Size(max = 30) String grntTypeCd,
      @NotNull @Positive BigDecimal grntAmt,
      @Size(max = 100) String issuerNm,
      @Size(max = 50) String policyNo,
      @NotNull LocalDate startDt,
      @NotNull LocalDate endDt,
      GuaranteeStatus status) {}

  public record AssocRequest(
      @NotNull Association assoc, @Size(max = 50) String assocRegNo, LocalDate regDt) {}

  public record SanctionRequest(
      @NotNull @Size(max = 30) String sanctionOrgCd,
      @NotNull @Size(max = 30) String sanctionTypeCd,
      @NotNull LocalDate startDt,
      LocalDate endDt,
      @Size(max = 400) String rsnTxt,
      boolean recruitBlock) {}

  /** 모집자격 판정 응답 — 종목별 결과 + 사유 (설계서 5.4, 7.3). */
  public record EligibilityResponse(
      boolean substantiveEligible, List<LineResponse> lines, List<ReasonResponse> commonReasons) {

    static EligibilityResponse from(EligibilityResult r) {
      return new EligibilityResponse(
          r.substantiveEligible(),
          r.lines().stream()
              .map(
                  l ->
                      new LineResponse(
                          l.line().name(),
                          l.eligible(),
                          l.reasons().stream()
                              .map(d -> new ReasonResponse(d.field(), d.reason(), d.message()))
                              .toList()))
              .toList(),
          r.commonReasons().stream()
              .map(d -> new ReasonResponse(d.field(), d.reason(), d.message()))
              .toList());
    }
  }

  public record LineResponse(String line, boolean eligible, List<ReasonResponse> reasons) {}

  public record ReasonResponse(String field, String reason, String message) {}
}
