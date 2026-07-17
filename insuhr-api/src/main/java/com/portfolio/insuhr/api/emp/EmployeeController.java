package com.portfolio.insuhr.api.emp;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.common.response.ApiResponse;
import com.portfolio.insuhr.domain.emp.AppointType;
import com.portfolio.insuhr.domain.emp.Appointment;
import com.portfolio.insuhr.domain.emp.AppointmentRepository;
import com.portfolio.insuhr.domain.emp.Emp;
import com.portfolio.insuhr.domain.emp.EmpErrorCode;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.emp.EmpType;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 임직원·발령 API (설계서 7.2 EMP). */
@RestController
@RequestMapping("/api/v1")
public class EmployeeController {

  private final EmployeeService employeeService;
  private final EmpRepository empRepository;
  private final AppointmentRepository appointmentRepository;

  public EmployeeController(
      EmployeeService employeeService,
      EmpRepository empRepository,
      AppointmentRepository appointmentRepository) {
    this.employeeService = employeeService;
    this.empRepository = empRepository;
    this.appointmentRepository = appointmentRepository;
  }

  /** 입사 처리 — 인물 생성/연결 + 입사 발령 동시 생성 (설계서 7.2 {@code POST /employees}). */
  @PostMapping("/employees")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<HireResponse> hire(@Valid @RequestBody HireRequest request) {
    EmployeeService.HireResult result =
        employeeService.hire(
            new NewPerson(
                request.personNm(),
                request.rrn(),
                request.birthDt(),
                request.gender(),
                request.mobile(),
                request.email(),
                request.nationalityCd()),
            new EmployeeService.HireCommand(
                request.empType(),
                request.orgId(),
                request.jobGradeCd(),
                request.jobTitleCd(),
                request.hireDt()));
    return ApiResponse.ok(
        new HireResponse(result.empId(), result.empNo(), result.personId()),
        TraceIdProvider.current());
  }

  /** 임직원 상세 (설계서 7.2 {@code GET /employees/{empId}}). */
  @GetMapping("/employees/{empId}")
  @PreAuthorize("hasAuthority('employee.read')")
  @Transactional(readOnly = true)
  public ApiResponse<EmployeeResponse> get(@PathVariable Long empId) {
    return ApiResponse.ok(EmployeeResponse.from(requireEmp(empId)), TraceIdProvider.current());
  }

  /** 발령 이력 (설계서 7.2 상세의 일부). */
  @GetMapping("/employees/{empId}/appointments")
  @PreAuthorize("hasAuthority('employee.read')")
  @Transactional(readOnly = true)
  public ApiResponse<List<AppointmentResponse>> appointments(@PathVariable Long empId) {
    List<AppointmentResponse> body =
        appointmentRepository.findByEmpIdOrderByAppointDtDescIdDesc(empId).stream()
            .map(AppointmentResponse::from)
            .toList();
    return ApiResponse.ok(body, TraceIdProvider.current());
  }

  /** 발령 기안 (설계서 7.2 {@code POST /employees/{empId}/appointments}). */
  @PostMapping("/employees/{empId}/appointments")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<AppointmentDraftResponse> draftAppointment(
      @PathVariable Long empId, @Valid @RequestBody AppointmentRequest request) {
    Long appointId =
        employeeService.draftAppointment(
            empId,
            new EmployeeService.AppointmentCommand(
                request.type(),
                request.appointDt(),
                request.orgId(),
                request.jobGradeCd(),
                request.jobTitleCd(),
                request.rsnTxt()));
    return ApiResponse.ok(new AppointmentDraftResponse(appointId), TraceIdProvider.current());
  }

  /** 발령 확정 (설계서 7.2 {@code POST /appointments/{appointId}/confirm}). */
  @PostMapping("/appointments/{appointId}/confirm")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> confirmAppointment(@PathVariable Long appointId) {
    employeeService.confirmAppointment(appointId);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  /** 발령 취소 (설계서 7.2 {@code POST /appointments/{appointId}/cancel}). */
  @PostMapping("/appointments/{appointId}/cancel")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> cancelAppointment(@PathVariable Long appointId) {
    employeeService.cancelAppointment(appointId);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  private Emp requireEmp(Long empId) {
    return empRepository
        .findById(empId)
        .orElseThrow(
            () -> new BusinessException(EmpErrorCode.NOT_FOUND, "임직원을 찾을 수 없습니다: " + empId));
  }

  public record HireRequest(
      @NotBlank @Size(max = 100) String personNm,
      @NotBlank @Pattern(regexp = "\\d{6}-?\\d{7}", message = "주민등록번호 형식이 올바르지 않습니다.") String rrn,
      @NotNull LocalDate birthDt,
      @NotNull Gender gender,
      String mobile,
      @Size(max = 100) String email,
      @Size(max = 10) String nationalityCd,
      @NotNull EmpType empType,
      @NotNull Long orgId,
      @Size(max = 10) String jobGradeCd,
      @Size(max = 10) String jobTitleCd,
      @NotNull LocalDate hireDt) {}

  public record HireResponse(Long empId, String empNo, Long personId) {}

  public record AppointmentRequest(
      @NotNull AppointType type,
      @NotNull LocalDate appointDt,
      @NotNull Long orgId,
      @Size(max = 10) String jobGradeCd,
      @Size(max = 10) String jobTitleCd,
      @Size(max = 400) String rsnTxt) {}

  public record AppointmentDraftResponse(Long appointId) {}

  public record EmployeeResponse(
      Long empId,
      String empNo,
      Long personId,
      String empTypeCd,
      Long orgId,
      String jobGradeCd,
      String jobTitleCd,
      LocalDate hireDt,
      LocalDate resignDt,
      String empStatusCd) {

    static EmployeeResponse from(Emp emp) {
      return new EmployeeResponse(
          emp.getId(),
          emp.getEmpNo(),
          emp.getPersonId(),
          emp.getEmpType().name(),
          emp.getOrgId(),
          emp.getJobGradeCd(),
          emp.getJobTitleCd(),
          emp.getHireDt(),
          emp.getResignDt(),
          emp.getStatus().name());
    }
  }

  public record AppointmentResponse(
      Long appointId,
      String appointTypeCd,
      LocalDate appointDt,
      Long orgId,
      String jobGradeCd,
      String jobTitleCd,
      String empStatusCd,
      String docStatusCd,
      String rsnTxt) {

    static AppointmentResponse from(Appointment a) {
      return new AppointmentResponse(
          a.getId(),
          a.getType().name(),
          a.getAppointDt(),
          a.getOrgId(),
          a.getJobGradeCd(),
          a.getJobTitleCd(),
          a.getStatus().name(),
          a.getDocStatus().name(),
          a.getRsnTxt());
    }
  }
}
