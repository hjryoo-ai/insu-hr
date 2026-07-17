package com.portfolio.insuhr.api.emp;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import com.portfolio.insuhr.domain.emp.EmpCareer;
import com.portfolio.insuhr.domain.emp.EmpCert;
import com.portfolio.insuhr.domain.emp.EmpEdu;
import com.portfolio.insuhr.domain.emp.EmpFamily;
import com.portfolio.insuhr.domain.emp.EmpRewardPunish;
import com.portfolio.insuhr.domain.emp.EmpSchool;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인사기록카드 6종 API (설계서 7.2 {@code /employees/{empId}/schools · careers · families · certs ·
 * educations · reward-punishments}).
 *
 * <p>여섯 하위 리소스가 전부 목록/추가/수정/삭제로 동일해 한 컨트롤러에 둔다. 규칙이 없는 단순 CRUD라 서비스도 하나({@link
 * EmpRecordCardService})다.
 */
@RestController
@RequestMapping("/api/v1/employees/{empId}")
public class EmpRecordCardController {

  private final EmpRecordCardService service;

  public EmpRecordCardController(EmpRecordCardService service) {
    this.service = service;
  }

  // ── 학력 ──────────────────────────────────────────────
  @GetMapping("/schools")
  @PreAuthorize("hasAuthority('employee.read')")
  public ApiResponse<List<EmpSchool>> schools(@PathVariable Long empId) {
    return ApiResponse.ok(service.schools(empId), TraceIdProvider.current());
  }

  @PostMapping("/schools")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<CreatedResponse> addSchool(
      @PathVariable Long empId, @Valid @RequestBody SchoolRequest r) {
    Long id =
        service.addSchool(
            empId, r.schoolNm(), r.majorNm(), r.degreeCd(), r.gradDt(), r.gradStatusCd());
    return ApiResponse.ok(new CreatedResponse(id), TraceIdProvider.current());
  }

  @PutMapping("/schools/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> updateSchool(
      @PathVariable Long empId, @PathVariable Long id, @Valid @RequestBody SchoolRequest r) {
    service.updateSchool(id, r.schoolNm(), r.majorNm(), r.degreeCd(), r.gradDt(), r.gradStatusCd());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  @DeleteMapping("/schools/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> deleteSchool(@PathVariable Long empId, @PathVariable Long id) {
    service.deleteSchool(id);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  // ── 경력 ──────────────────────────────────────────────
  @GetMapping("/careers")
  @PreAuthorize("hasAuthority('employee.read')")
  public ApiResponse<List<EmpCareer>> careers(@PathVariable Long empId) {
    return ApiResponse.ok(service.careers(empId), TraceIdProvider.current());
  }

  @PostMapping("/careers")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<CreatedResponse> addCareer(
      @PathVariable Long empId, @Valid @RequestBody CareerRequest r) {
    Long id =
        service.addCareer(
            empId, r.companyNm(), r.deptNm(), r.positionNm(), r.joinDt(), r.leaveDt(), r.jobDesc());
    return ApiResponse.ok(new CreatedResponse(id), TraceIdProvider.current());
  }

  @PutMapping("/careers/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> updateCareer(
      @PathVariable Long empId, @PathVariable Long id, @Valid @RequestBody CareerRequest r) {
    service.updateCareer(
        id, r.companyNm(), r.deptNm(), r.positionNm(), r.joinDt(), r.leaveDt(), r.jobDesc());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  @DeleteMapping("/careers/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> deleteCareer(@PathVariable Long empId, @PathVariable Long id) {
    service.deleteCareer(id);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  // ── 가족 ──────────────────────────────────────────────
  @GetMapping("/families")
  @PreAuthorize("hasAuthority('employee.read')")
  public ApiResponse<List<EmpFamily>> families(@PathVariable Long empId) {
    return ApiResponse.ok(service.families(empId), TraceIdProvider.current());
  }

  @PostMapping("/families")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<CreatedResponse> addFamily(
      @PathVariable Long empId, @Valid @RequestBody FamilyRequest r) {
    Long id = service.addFamily(empId, r.relationCd(), r.familyNm(), r.birthDt(), r.cohabitYn());
    return ApiResponse.ok(new CreatedResponse(id), TraceIdProvider.current());
  }

  @PutMapping("/families/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> updateFamily(
      @PathVariable Long empId, @PathVariable Long id, @Valid @RequestBody FamilyRequest r) {
    service.updateFamily(id, r.relationCd(), r.familyNm(), r.birthDt(), r.cohabitYn());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  @DeleteMapping("/families/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> deleteFamily(@PathVariable Long empId, @PathVariable Long id) {
    service.deleteFamily(id);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  // ── 자격증 ────────────────────────────────────────────
  @GetMapping("/certs")
  @PreAuthorize("hasAuthority('employee.read')")
  public ApiResponse<List<EmpCert>> certs(@PathVariable Long empId) {
    return ApiResponse.ok(service.certs(empId), TraceIdProvider.current());
  }

  @PostMapping("/certs")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<CreatedResponse> addCert(
      @PathVariable Long empId, @Valid @RequestBody CertRequest r) {
    Long id =
        service.addCert(empId, r.certNm(), r.issuerNm(), r.certNo(), r.acquireDt(), r.expireDt());
    return ApiResponse.ok(new CreatedResponse(id), TraceIdProvider.current());
  }

  @PutMapping("/certs/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> updateCert(
      @PathVariable Long empId, @PathVariable Long id, @Valid @RequestBody CertRequest r) {
    service.updateCert(id, r.certNm(), r.issuerNm(), r.certNo(), r.acquireDt(), r.expireDt());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  @DeleteMapping("/certs/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> deleteCert(@PathVariable Long empId, @PathVariable Long id) {
    service.deleteCert(id);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  // ── 교육 ──────────────────────────────────────────────
  @GetMapping("/educations")
  @PreAuthorize("hasAuthority('employee.read')")
  public ApiResponse<List<EmpEdu>> educations(@PathVariable Long empId) {
    return ApiResponse.ok(service.educations(empId), TraceIdProvider.current());
  }

  @PostMapping("/educations")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<CreatedResponse> addEdu(
      @PathVariable Long empId, @Valid @RequestBody EduRequest r) {
    Long id =
        service.addEdu(
            empId, r.eduNm(), r.eduTypeCd(), r.startDt(), r.endDt(), r.eduHours(), r.resultCd());
    return ApiResponse.ok(new CreatedResponse(id), TraceIdProvider.current());
  }

  @PutMapping("/educations/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> updateEdu(
      @PathVariable Long empId, @PathVariable Long id, @Valid @RequestBody EduRequest r) {
    service.updateEdu(
        id, r.eduNm(), r.eduTypeCd(), r.startDt(), r.endDt(), r.eduHours(), r.resultCd());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  @DeleteMapping("/educations/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> deleteEdu(@PathVariable Long empId, @PathVariable Long id) {
    service.deleteEdu(id);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  // ── 상벌 ──────────────────────────────────────────────
  @GetMapping("/reward-punishments")
  @PreAuthorize("hasAuthority('employee.read')")
  public ApiResponse<List<EmpRewardPunish>> rewardPunishments(@PathVariable Long empId) {
    return ApiResponse.ok(service.rewardPunishments(empId), TraceIdProvider.current());
  }

  @PostMapping("/reward-punishments")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<CreatedResponse> addRewardPunish(
      @PathVariable Long empId, @Valid @RequestBody RewardPunishRequest r) {
    Long id = service.addRewardPunish(empId, r.rpTypeCd(), r.rpCd(), r.rpDt(), r.rsnTxt());
    return ApiResponse.ok(new CreatedResponse(id), TraceIdProvider.current());
  }

  @PutMapping("/reward-punishments/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> updateRewardPunish(
      @PathVariable Long empId, @PathVariable Long id, @Valid @RequestBody RewardPunishRequest r) {
    service.updateRewardPunish(id, r.rpTypeCd(), r.rpCd(), r.rpDt(), r.rsnTxt());
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  @DeleteMapping("/reward-punishments/{id}")
  @PreAuthorize("hasAuthority('employee.write')")
  public ApiResponse<Void> deleteRewardPunish(@PathVariable Long empId, @PathVariable Long id) {
    service.deleteRewardPunish(id);
    return ApiResponse.ok(null, TraceIdProvider.current());
  }

  public record CreatedResponse(Long id) {}

  public record SchoolRequest(
      @NotBlank String schoolNm,
      String majorNm,
      @NotBlank String degreeCd,
      LocalDate gradDt,
      @NotBlank String gradStatusCd) {}

  public record CareerRequest(
      @NotBlank String companyNm,
      String deptNm,
      String positionNm,
      LocalDate joinDt,
      LocalDate leaveDt,
      String jobDesc) {}

  public record FamilyRequest(
      @NotBlank String relationCd,
      @NotBlank String familyNm,
      LocalDate birthDt,
      boolean cohabitYn) {}

  public record CertRequest(
      @NotBlank String certNm,
      String issuerNm,
      String certNo,
      LocalDate acquireDt,
      LocalDate expireDt) {}

  public record EduRequest(
      @NotBlank String eduNm,
      @NotBlank String eduTypeCd,
      LocalDate startDt,
      LocalDate endDt,
      BigDecimal eduHours,
      @NotBlank String resultCd) {}

  public record RewardPunishRequest(
      @NotBlank String rpTypeCd, @NotBlank String rpCd, LocalDate rpDt, String rsnTxt) {}
}
