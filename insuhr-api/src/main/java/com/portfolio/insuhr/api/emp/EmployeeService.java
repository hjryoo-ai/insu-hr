package com.portfolio.insuhr.api.emp;

import com.portfolio.insuhr.api.person.PersonService;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.emp.AppointType;
import com.portfolio.insuhr.domain.emp.Appointment;
import com.portfolio.insuhr.domain.emp.AppointmentApplyService;
import com.portfolio.insuhr.domain.emp.AppointmentRepository;
import com.portfolio.insuhr.domain.emp.Emp;
import com.portfolio.insuhr.domain.emp.EmpErrorCode;
import com.portfolio.insuhr.domain.emp.EmpNoGenerator;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.emp.EmpStatus;
import com.portfolio.insuhr.domain.emp.EmpType;
import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.integration.IntegrationRecorder;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgErrorCode;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 임직원·발령 유스케이스 (설계서 7.2 EMP, 5.5).
 *
 * <p>발령 반영 규칙 자체는 {@link AppointmentApplyService}(도메인)가 갖는다. 이 애플리케이션 서비스는 유스케이스 경계(입사·기안·확정·취소)와
 * 트랜잭션, 그리고 이벤트 발행을 엮는다.
 *
 * <p><b>확정이 스냅샷을 물질화한다</b>: 확정은 발령 문서 상태를 CONFIRMED로 바꾸고 곧바로 재계산을 돌린다. 발령일이 오늘 이하면 스냅샷이 바뀌고, 미래면
 * 재계산 결과가 그대로라 아무것도 안 바뀐다(예약분) — 그 미래분은 Phase 7 배치가 발령일에 다시 재계산해 반영한다. 온라인과 배치가 <b>같은 함수</b>를 부르므로
 * 경로가 갈라지지 않는다(설계서 5.5).
 */
@Service
public class EmployeeService {

  private final PersonService personService;
  private final EmpRepository empRepository;
  private final AppointmentRepository appointmentRepository;
  private final AppointmentApplyService appointmentApplyService;
  private final EmpNoGenerator empNoGenerator;
  private final OrgRepository orgRepository;
  private final IntegrationRecorder integrationRecorder;

  public EmployeeService(
      PersonService personService,
      EmpRepository empRepository,
      AppointmentRepository appointmentRepository,
      AppointmentApplyService appointmentApplyService,
      EmpNoGenerator empNoGenerator,
      OrgRepository orgRepository,
      IntegrationRecorder integrationRecorder) {
    this.personService = personService;
    this.empRepository = empRepository;
    this.appointmentRepository = appointmentRepository;
    this.appointmentApplyService = appointmentApplyService;
    this.empNoGenerator = empNoGenerator;
    this.orgRepository = orgRepository;
    this.integrationRecorder = integrationRecorder;
  }

  /**
   * 입사 처리 (설계서 7.2 {@code POST /employees}).
   *
   * <p>인물 생성/연결 + 임직원 역할 생성 + 입사 발령을 한 트랜잭션에서 한다. 인물은 이미 있으면 재사용된다(설계서 5.2) — 직원 퇴사 후 재입사, 혹은 설계사였던
   * 사람의 입사가 이 경로로 들어온다.
   *
   * <p>입사 발령은 만들자마자 확정한다. 입사는 예약할 일이 아니라 이미 일어난 사실이기 때문이다.
   */
  @Transactional
  public HireResult hire(NewPerson person, HireCommand command) {
    Long personId = personService.register(person).personId();

    // 인물 1명당 임직원 역할은 0..1 (설계서 5.2). 실제 방어선은 UX_EMP_PERSON 유니크 인덱스이고,
    // 이 검사는 정상 케이스에서 유니크 예외 대신 업무 메시지를 주기 위한 것이다.
    empRepository
        .findByPersonId(personId)
        .ifPresent(
            existing -> {
              throw new BusinessException(
                  EmpErrorCode.ALREADY_EMPLOYEE, "이미 임직원으로 등록된 인물입니다. empId=" + existing.getId());
            });

    Org org = requireActiveOrg(command.orgId(), command.hireDt());

    Emp emp =
        empRepository.save(
            Emp.hire(
                personId,
                empNoGenerator.next(),
                command.empType(),
                org.getId(),
                command.jobGradeCd(),
                command.jobTitleCd(),
                command.hireDt()));

    Appointment hire =
        Appointment.draft(
            emp.getId(),
            AppointType.HIRE,
            command.hireDt(),
            org.getId(),
            command.jobGradeCd(),
            command.jobTitleCd(),
            emp.getStatus(),
            "입사");
    hire.confirm();
    appointmentRepository.save(hire);

    // 입사 발령의 값이 Emp.hire 초기값과 같아 재계산은 값을 안 바꾼다. 그래도 이벤트는 입사 사실로 발행한다.
    integrationRecorder.record(
        IntegrationEvent.created(
            "emp.hired", "EMP", emp.getId(), emp.getEmpNo(), emp.toSnapshot()));

    return new HireResult(emp.getId(), emp.getEmpNo(), personId);
  }

  /**
   * 발령 기안 (설계서 7.2 {@code POST /employees/{empId}/appointments}).
   *
   * <p>재직상태는 입력받지 않는다 — 발령유형에서 파생하거나 발령일 시점의 직전 상태를 물려받는다. 그 "직전 상태"는 발령일 하루 전까지의 스냅샷을 계산해 얻는다.
   */
  @Transactional
  public Long draftAppointment(Long empId, AppointmentCommand command) {
    Emp emp = requireEmp(empId);
    if (emp.isResigned()) {
      throw new BusinessException(EmpErrorCode.RESIGNED, "퇴직한 임직원에게는 발령을 낼 수 없습니다. empId=" + empId);
    }
    if (command.appointDt().isBefore(emp.getHireDt())) {
      throw new BusinessException(
          EmpErrorCode.INVALID_APPOINT_DT, "발령일이 입사일보다 앞설 수 없습니다. 입사일=" + emp.getHireDt());
    }
    requireActiveOrg(command.orgId(), command.appointDt());

    Appointment appointment =
        Appointment.draft(
            empId,
            command.type(),
            command.appointDt(),
            command.orgId(),
            command.jobGradeCd(),
            command.jobTitleCd(),
            baseStatusFor(emp, command.appointDt()),
            command.rsnTxt());
    return appointmentRepository.save(appointment).getId();
  }

  /**
   * 발령 확정 (설계서 7.2 {@code POST /appointments/{appointId}/confirm}).
   *
   * <p>확정 후 곧바로 재계산한다. 발령일이 오늘 이하면 스냅샷이 바뀌고 이벤트가 나간다. 미래면 스냅샷이 그대로라 이벤트도 없다 — 그 반영은 발령일 배치의 몫이다.
   */
  @Transactional
  public void confirmAppointment(Long appointId) {
    Appointment appointment = requireAppointment(appointId);
    appointment.confirm();

    boolean snapshotChanged = appointmentApplyService.recalculate(appointment.getEmpId());
    if (snapshotChanged) {
      publishAppointmentApplied(appointment.getEmpId());
    }
  }

  /**
   * 발령 취소 (설계서 7.2 {@code POST /appointments/{appointId}/cancel}).
   *
   * <p>이미 반영된 발령은 취소할 수 없다(설계서 5.5 v1.4) — 반영된 과거를 지우면 이력·스냅샷이 어긋난다. 엔티티가 오늘 기준으로 이 규칙을 지킨다.
   */
  @Transactional
  public void cancelAppointment(Long appointId) {
    Appointment appointment = requireAppointment(appointId);
    appointment.cancel(appointmentApplyService.today());
  }

  private void publishAppointmentApplied(Long empId) {
    Emp emp = requireEmp(empId);
    integrationRecorder.record(
        IntegrationEvent.updated(
            "emp.appointment.applied", "EMP", emp.getId(), emp.getEmpNo(), emp.toSnapshot()));
  }

  /**
   * 발령일 시점의 직전 재직상태.
   *
   * <p>발령 하루 전까지의 스냅샷을 계산한다. 그 시점 스냅샷이 없으면(입사 발령 자체 등) 현재 상태를 쓴다. 이 값은 발령유형이 상태를 강제하지 않을 때(승진·전보 등)
   * 물려받는 상태다 — {@link Appointment#draft}가 정한다.
   */
  private EmpStatus baseStatusFor(Emp emp, LocalDate appointDt) {
    return appointmentApplyService
        .snapshotAsOf(emp.getId(), appointDt.minusDays(1))
        .orElseGet(emp::currentSnapshot)
        .status();
  }

  private Org requireActiveOrg(Long orgId, LocalDate on) {
    Org org =
        orgRepository
            .findById(orgId)
            .orElseThrow(
                () -> new BusinessException(OrgErrorCode.NOT_FOUND, "조직을 찾을 수 없습니다: " + orgId));
    if (!org.isActiveOn(on)) {
      throw new BusinessException(
          EmpErrorCode.ORG_CLOSED, "폐지되었거나 아직 개설 전인 조직으로 발령할 수 없습니다. 조직=" + org.getOrgCd());
    }
    return org;
  }

  private Emp requireEmp(Long empId) {
    return empRepository
        .findById(empId)
        .orElseThrow(
            () -> new BusinessException(EmpErrorCode.NOT_FOUND, "임직원을 찾을 수 없습니다: " + empId));
  }

  private Appointment requireAppointment(Long appointId) {
    return appointmentRepository
        .findById(appointId)
        .orElseThrow(
            () ->
                new BusinessException(
                    EmpErrorCode.APPOINT_NOT_FOUND, "발령을 찾을 수 없습니다: " + appointId));
  }

  /** 입사 명령. 인물 정보(NewPerson)와 분리해 받는다 — 인물은 재사용될 수 있어 입사 파라미터가 아니다. */
  public record HireCommand(
      EmpType empType, Long orgId, String jobGradeCd, String jobTitleCd, LocalDate hireDt) {}

  public record HireResult(Long empId, String empNo, Long personId) {}

  /** 발령 기안 명령. 재직상태는 파생되므로 입력에 없다(설계서 5.5). */
  public record AppointmentCommand(
      AppointType type,
      LocalDate appointDt,
      Long orgId,
      String jobGradeCd,
      String jobTitleCd,
      String rsnTxt) {}
}
