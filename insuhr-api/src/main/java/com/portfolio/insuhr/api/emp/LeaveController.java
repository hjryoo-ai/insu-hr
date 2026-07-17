package com.portfolio.insuhr.api.emp;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import com.portfolio.insuhr.domain.leave.LeaveGrant;
import com.portfolio.insuhr.domain.leave.LeaveRequest;
import com.portfolio.insuhr.domain.leave.LeaveType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

/** 휴가·연차 API (설계서 7.2 {@code /employees/{empId}/leaves}, {@code /leaves/{id}/approve}). */
@RestController
@RequestMapping("/api/v1")
public class LeaveController {

  private final LeaveService leaveService;

  public LeaveController(LeaveService leaveService) {
    this.leaveService = leaveService;
  }

  /** 연차 부여 (설계서 8장 배치 외에 수동 부여 경로). */
  @PostMapping("/employees/{empId}/leave-grants")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<CreatedResponse> grant(
      @PathVariable Long empId, @Valid @RequestBody GrantRequest r) {
    Long id = leaveService.grant(empId, r.yearNo(), r.grantDays(), r.expireDt());
    return ApiResponse.ok(new CreatedResponse(id), TraceIdProvider.current());
  }

  @GetMapping("/employees/{empId}/leave-grants")
  @PreAuthorize("hasAuthority('employee.read')")
  public ApiResponse<List<GrantResponse>> grants(@PathVariable Long empId) {
    return ApiResponse.ok(
        leaveService.grants(empId).stream().map(GrantResponse::from).toList(),
        TraceIdProvider.current());
  }

  /** 휴가 신청 (설계서 7.2 {@code POST /employees/{empId}/leaves}). */
  @PostMapping("/employees/{empId}/leaves")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<CreatedResponse> request(
      @PathVariable Long empId, @Valid @RequestBody LeaveRequestBody r) {
    Long id =
        leaveService.requestLeave(empId, r.type(), r.startDt(), r.endDt(), r.days(), r.rsnTxt());
    return ApiResponse.ok(new CreatedResponse(id), TraceIdProvider.current());
  }

  @GetMapping("/employees/{empId}/leaves")
  @PreAuthorize("hasAuthority('employee.read')")
  public ApiResponse<List<LeaveResponse>> leaves(@PathVariable Long empId) {
    return ApiResponse.ok(
        leaveService.requests(empId).stream().map(LeaveResponse::from).toList(),
        TraceIdProvider.current());
  }

  /** 휴가 승인 (설계서 7.2 {@code POST /leaves/{id}/approve}). 연차면 이 시점에 잔여가 차감된다. */
  @PostMapping("/leaves/{leaveReqId}/approve")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> approve(
      @PathVariable Long leaveReqId, @Valid @RequestBody ApproveRequest r) {
    leaveService.approve(leaveReqId, r.approverEmpId());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  @PostMapping("/leaves/{leaveReqId}/reject")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> reject(
      @PathVariable Long leaveReqId, @Valid @RequestBody ApproveRequest r) {
    leaveService.reject(leaveReqId, r.approverEmpId());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  @PostMapping("/leaves/{leaveReqId}/cancel")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> cancel(@PathVariable Long leaveReqId) {
    leaveService.cancel(leaveReqId);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  public record CreatedResponse(Long id) {}

  public record GrantRequest(
      @NotNull Integer yearNo,
      @NotNull @Positive BigDecimal grantDays,
      @NotNull LocalDate expireDt) {}

  public record LeaveRequestBody(
      @NotNull LeaveType type,
      @NotNull LocalDate startDt,
      @NotNull LocalDate endDt,
      @NotNull @Positive BigDecimal days,
      String rsnTxt) {}

  public record ApproveRequest(@NotNull Long approverEmpId) {}

  public record GrantResponse(
      Long grantId,
      int yearNo,
      BigDecimal grantDays,
      BigDecimal usedDays,
      BigDecimal remainingDays,
      LocalDate expireDt) {

    static GrantResponse from(LeaveGrant g) {
      return new GrantResponse(
          g.getId(),
          g.getYearNo(),
          g.getGrantDays(),
          g.getUsedDays(),
          g.remainingDays(),
          g.getExpireDt());
    }
  }

  public record LeaveResponse(
      Long leaveReqId,
      String leaveTypeCd,
      LocalDate startDt,
      LocalDate endDt,
      BigDecimal days,
      String statusCd,
      Long approverEmpId) {

    static LeaveResponse from(LeaveRequest req) {
      return new LeaveResponse(
          req.getId(),
          req.getType().name(),
          req.getStartDt(),
          req.getEndDt(),
          req.getDays(),
          req.getStatus().name(),
          req.getApproverEmpId());
    }
  }
}
