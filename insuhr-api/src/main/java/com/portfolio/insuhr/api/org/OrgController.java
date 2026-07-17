package com.portfolio.insuhr.api.org;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 조직 API (설계서 7.2 ORG). */
@RestController
@RequestMapping("/api/v1/orgs")
public class OrgController {

  private final OrgService orgService;

  public OrgController(OrgService orgService) {
    this.orgService = orgService;
  }

  /**
   * 조직도 트리 (기준일자 시점 조회 지원).
   *
   * @param asOfDate 생략하면 오늘. 과거 일자를 주면 그 시점의 조직도가 나온다
   */
  @GetMapping("/tree")
  @PreAuthorize("hasAuthority('org.read')")
  public ApiResponse<List<OrgService.OrgTreeNode>> tree(
      @RequestParam(required = false) OrgType type,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOfDate) {
    LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;
    return ApiResponse.ok(orgService.tree(asOf, type), TraceIdProvider.current());
  }

  @GetMapping("/{orgCd}")
  @PreAuthorize("hasAuthority('org.read')")
  public ApiResponse<OrgResponse> get(@PathVariable String orgCd) {
    return ApiResponse.ok(OrgResponse.from(orgService.get(orgCd)), TraceIdProvider.current());
  }

  /** 조직 신설. */
  @PostMapping
  @PreAuthorize("hasAuthority('org.write')")
  public ApiResponse<OrgResponse> create(@Valid @RequestBody CreateOrgRequest request) {
    orgService.create(
        request.orgCd(),
        request.orgNm(),
        request.orgType(),
        request.upOrgCd(),
        request.sortOrd() == null ? 0 : request.sortOrd(),
        request.validFromDt() == null ? LocalDate.now() : request.validFromDt());
    return ApiResponse.ok(
        OrgResponse.from(orgService.get(request.orgCd())), TraceIdProvider.current());
  }

  /** 명칭변경/이관 (이력 자동 기록 + Outbox). */
  @PutMapping("/{orgCd}")
  @PreAuthorize("hasAuthority('org.write')")
  public ApiResponse<OrgResponse> update(
      @PathVariable String orgCd, @Valid @RequestBody UpdateOrgRequest request) {
    orgService.update(
        orgCd,
        request.orgNm(),
        request.upOrgCd(),
        request.effectiveDt() == null ? LocalDate.now() : request.effectiveDt(),
        request.reason());
    return ApiResponse.ok(OrgResponse.from(orgService.get(orgCd)), TraceIdProvider.current());
  }

  /** 조직 폐지. 하위조직/소속인원 존재 시 409. */
  @PostMapping("/{orgCd}/close")
  @PreAuthorize("hasAuthority('org.write')")
  public ApiResponse<Void> close(
      @PathVariable String orgCd, @Valid @RequestBody CloseOrgRequest request) {
    orgService.close(
        orgCd,
        request.closeDate() == null ? LocalDate.now() : request.closeDate(),
        request.reason());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  public record CreateOrgRequest(
      @NotBlank @Size(max = 10) String orgCd,
      @NotBlank @Size(max = 100) String orgNm,
      @NotNull OrgType orgType,
      String upOrgCd,
      Integer sortOrd,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFromDt) {}

  public record UpdateOrgRequest(
      @Size(max = 100) String orgNm,
      String upOrgCd,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDt,
      @Size(max = 400) String reason) {}

  public record CloseOrgRequest(
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closeDate,
      @Size(max = 400) String reason) {}

  public record OrgResponse(
      Long orgId,
      String orgCd,
      String orgNm,
      String orgTypeCd,
      Long upOrgId,
      int orgLvl,
      LocalDate validFromDt,
      LocalDate validToDt,
      String useYn) {

    static OrgResponse from(Org org) {
      return new OrgResponse(
          org.getId(),
          org.getOrgCd(),
          org.getOrgNm(),
          org.getOrgTypeCd(),
          org.getUpOrgId(),
          org.getOrgLvl(),
          org.getValidFromDt(),
          org.getValidToDt(),
          org.isUseYn() ? "Y" : "N");
    }
  }
}
