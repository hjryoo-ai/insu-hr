package com.portfolio.insuhr.api.emp;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.emp.EmpCareer;
import com.portfolio.insuhr.domain.emp.EmpCareerRepository;
import com.portfolio.insuhr.domain.emp.EmpCert;
import com.portfolio.insuhr.domain.emp.EmpCertRepository;
import com.portfolio.insuhr.domain.emp.EmpEdu;
import com.portfolio.insuhr.domain.emp.EmpEduRepository;
import com.portfolio.insuhr.domain.emp.EmpErrorCode;
import com.portfolio.insuhr.domain.emp.EmpFamily;
import com.portfolio.insuhr.domain.emp.EmpFamilyRepository;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.emp.EmpRewardPunish;
import com.portfolio.insuhr.domain.emp.EmpRewardPunishRepository;
import com.portfolio.insuhr.domain.emp.EmpSchool;
import com.portfolio.insuhr.domain.emp.EmpSchoolRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인사기록카드 6종 CRUD (설계서 7.2, 6.5).
 *
 * <p>학력/경력/가족/자격증/교육/상벌. 전부 직원에 종속된 단순 이력이라 상태머신도 이벤트 발행도 없다 — 기준정보가 아니라 인사 내부 기록이기 때문이다(설계서 9.3의
 * 이벤트 카탈로그에 없다). 그래서 {@link com.portfolio.insuhr.domain.integration.IntegrationRecorder}를 거치지 않는다.
 *
 * <p>여섯 종을 한 서비스에 두는 이유: 로직이 "직원 존재 확인 → CRUD"로 동일하고 서로 규칙이 없다. 규칙이 생기는 항목이 있으면 그것만 분리한다.
 */
@Service
@Transactional
public class EmpRecordCardService {

  private final EmpRepository empRepository;
  private final EmpSchoolRepository schoolRepository;
  private final EmpCareerRepository careerRepository;
  private final EmpFamilyRepository familyRepository;
  private final EmpCertRepository certRepository;
  private final EmpEduRepository eduRepository;
  private final EmpRewardPunishRepository rewardPunishRepository;

  public EmpRecordCardService(
      EmpRepository empRepository,
      EmpSchoolRepository schoolRepository,
      EmpCareerRepository careerRepository,
      EmpFamilyRepository familyRepository,
      EmpCertRepository certRepository,
      EmpEduRepository eduRepository,
      EmpRewardPunishRepository rewardPunishRepository) {
    this.empRepository = empRepository;
    this.schoolRepository = schoolRepository;
    this.careerRepository = careerRepository;
    this.familyRepository = familyRepository;
    this.certRepository = certRepository;
    this.eduRepository = eduRepository;
    this.rewardPunishRepository = rewardPunishRepository;
  }

  // ── 학력 ──────────────────────────────────────────────
  public Long addSchool(
      Long empId,
      String schoolNm,
      String majorNm,
      String degreeCd,
      LocalDate gradDt,
      String gradStatusCd) {
    requireEmp(empId);
    return schoolRepository
        .save(new EmpSchool(empId, schoolNm, majorNm, degreeCd, gradDt, gradStatusCd))
        .getId();
  }

  public void updateSchool(
      Long id,
      String schoolNm,
      String majorNm,
      String degreeCd,
      LocalDate gradDt,
      String gradStatusCd) {
    EmpSchool school = schoolRepository.findById(id).orElseThrow(() -> recordNotFound("학력", id));
    school.update(schoolNm, majorNm, degreeCd, gradDt, gradStatusCd);
  }

  public void deleteSchool(Long id) {
    schoolRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<EmpSchool> schools(Long empId) {
    return schoolRepository.findByEmpIdOrderByGradDtDesc(empId);
  }

  // ── 경력 ──────────────────────────────────────────────
  public Long addCareer(
      Long empId,
      String companyNm,
      String deptNm,
      String positionNm,
      LocalDate joinDt,
      LocalDate leaveDt,
      String jobDesc) {
    requireEmp(empId);
    return careerRepository
        .save(new EmpCareer(empId, companyNm, deptNm, positionNm, joinDt, leaveDt, jobDesc))
        .getId();
  }

  public void updateCareer(
      Long id,
      String companyNm,
      String deptNm,
      String positionNm,
      LocalDate joinDt,
      LocalDate leaveDt,
      String jobDesc) {
    EmpCareer career = careerRepository.findById(id).orElseThrow(() -> recordNotFound("경력", id));
    career.update(companyNm, deptNm, positionNm, joinDt, leaveDt, jobDesc);
  }

  public void deleteCareer(Long id) {
    careerRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<EmpCareer> careers(Long empId) {
    return careerRepository.findByEmpIdOrderByJoinDtDesc(empId);
  }

  // ── 가족 ──────────────────────────────────────────────
  public Long addFamily(
      Long empId, String relationCd, String familyNm, LocalDate birthDt, boolean cohabitYn) {
    requireEmp(empId);
    return familyRepository
        .save(new EmpFamily(empId, relationCd, familyNm, birthDt, cohabitYn))
        .getId();
  }

  public void updateFamily(
      Long id, String relationCd, String familyNm, LocalDate birthDt, boolean cohabitYn) {
    EmpFamily family = familyRepository.findById(id).orElseThrow(() -> recordNotFound("가족", id));
    family.update(relationCd, familyNm, birthDt, cohabitYn);
  }

  public void deleteFamily(Long id) {
    familyRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<EmpFamily> families(Long empId) {
    return familyRepository.findByEmpIdOrderByIdAsc(empId);
  }

  // ── 자격증 ────────────────────────────────────────────
  public Long addCert(
      Long empId,
      String certNm,
      String issuerNm,
      String certNo,
      LocalDate acquireDt,
      LocalDate expireDt) {
    requireEmp(empId);
    return certRepository
        .save(new EmpCert(empId, certNm, issuerNm, certNo, acquireDt, expireDt))
        .getId();
  }

  public void updateCert(
      Long id,
      String certNm,
      String issuerNm,
      String certNo,
      LocalDate acquireDt,
      LocalDate expireDt) {
    EmpCert cert = certRepository.findById(id).orElseThrow(() -> recordNotFound("자격증", id));
    cert.update(certNm, issuerNm, certNo, acquireDt, expireDt);
  }

  public void deleteCert(Long id) {
    certRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<EmpCert> certs(Long empId) {
    return certRepository.findByEmpIdOrderByAcquireDtDesc(empId);
  }

  // ── 교육 ──────────────────────────────────────────────
  public Long addEdu(
      Long empId,
      String eduNm,
      String eduTypeCd,
      LocalDate startDt,
      LocalDate endDt,
      BigDecimal eduHours,
      String resultCd) {
    requireEmp(empId);
    return eduRepository
        .save(new EmpEdu(empId, eduNm, eduTypeCd, startDt, endDt, eduHours, resultCd))
        .getId();
  }

  public void updateEdu(
      Long id,
      String eduNm,
      String eduTypeCd,
      LocalDate startDt,
      LocalDate endDt,
      BigDecimal eduHours,
      String resultCd) {
    EmpEdu edu = eduRepository.findById(id).orElseThrow(() -> recordNotFound("교육", id));
    edu.update(eduNm, eduTypeCd, startDt, endDt, eduHours, resultCd);
  }

  public void deleteEdu(Long id) {
    eduRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<EmpEdu> educations(Long empId) {
    return eduRepository.findByEmpIdOrderByEndDtDesc(empId);
  }

  // ── 상벌 ──────────────────────────────────────────────
  public Long addRewardPunish(
      Long empId, String rpTypeCd, String rpCd, LocalDate rpDt, String rsnTxt) {
    requireEmp(empId);
    return rewardPunishRepository
        .save(new EmpRewardPunish(empId, rpTypeCd, rpCd, rpDt, rsnTxt))
        .getId();
  }

  public void updateRewardPunish(
      Long id, String rpTypeCd, String rpCd, LocalDate rpDt, String rsnTxt) {
    EmpRewardPunish rp =
        rewardPunishRepository.findById(id).orElseThrow(() -> recordNotFound("상벌", id));
    rp.update(rpTypeCd, rpCd, rpDt, rsnTxt);
  }

  public void deleteRewardPunish(Long id) {
    rewardPunishRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<EmpRewardPunish> rewardPunishments(Long empId) {
    return rewardPunishRepository.findByEmpIdOrderByRpDtDesc(empId);
  }

  private void requireEmp(Long empId) {
    if (!empRepository.existsById(empId)) {
      throw new BusinessException(EmpErrorCode.NOT_FOUND, "임직원을 찾을 수 없습니다: " + empId);
    }
  }

  private BusinessException recordNotFound(String kind, Long id) {
    return new BusinessException(EmpErrorCode.RECORD_NOT_FOUND, kind + " 기록을 찾을 수 없습니다: " + id);
  }
}
