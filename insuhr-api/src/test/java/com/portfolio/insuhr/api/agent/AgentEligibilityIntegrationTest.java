package com.portfolio.insuhr.api.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.AgentTestConfig;
import com.portfolio.insuhr.api.support.MutableClock;
import com.portfolio.insuhr.api.support.RecordingIntegrationRecorder;
import com.portfolio.insuhr.api.support.TestClockConfig;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.common.error.ErrorDetail;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.agent.AgentLicenseRepository;
import com.portfolio.insuhr.domain.agent.AgentRepository;
import com.portfolio.insuhr.domain.agent.AgentStatus;
import com.portfolio.insuhr.domain.agent.Association;
import com.portfolio.insuhr.domain.agent.Channel;
import com.portfolio.insuhr.domain.agent.EduType;
import com.portfolio.insuhr.domain.agent.GuaranteeStatus;
import com.portfolio.insuhr.domain.agent.LicenseStatus;
import com.portfolio.insuhr.domain.agent.LicenseType;
import com.portfolio.insuhr.domain.eligibility.AgentEligibilityReconciler;
import com.portfolio.insuhr.domain.eligibility.EligibilityResult;
import com.portfolio.insuhr.domain.eligibility.RecruitEligibilityService;
import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 모집자격 판정 통합 테스트 (설계서 5.4 v1.6, 시나리오 1b·3).
 *
 * <p><b>실판정 {@code EligibilityRequirementChecker}·{@code RecruitEligibilityService}를 실제 데이터로 돈다</b>
 * — {@code StubRequirementCheckerConfig}를 import하지 <b>않아서</b> 위촉 요건검증이 실제로 걸린다(시나리오 1b). {@code
 * AgentTestConfig}로 이벤트만 캡처한다.
 *
 * <p>경계 판정은 {@code evaluate(agentId, asOf)}에 날짜를 넘겨 확인한다(순수 함수라 시계를 옮길 필요가 없다). 자동 전이·이벤트는 clock 기준
 * reconciler로 확인한다.
 */
@Import({TestClockConfig.class, AgentTestConfig.class})
class AgentEligibilityIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);
  private static final BigDecimal MIN_GRNT = new BigDecimal("10000000"); // MIN_GRNT_AMT 시드값
  private static final LocalDate FAR = LocalDate.of(2035, 1, 1);

  @Autowired AgentService agentService;
  @Autowired AgentCredentialService credentialService;
  @Autowired AgentEligibilityReconciler reconciler;
  @Autowired RecruitEligibilityService eligibilityService;
  @Autowired AgentRepository agentRepository;
  @Autowired AgentLicenseRepository licenseRepository;
  @Autowired OrgRepository orgRepository;
  @Autowired MutableClock clock;
  @Autowired RecordingIntegrationRecorder recorder;

  @BeforeEach
  void setUp() {
    clock.setDate(LocalDate.of(2026, 3, 1));
    recorder.clear();
  }

  private Long newOrg() {
    int n = SEQ.getAndIncrement();
    return orgRepository
        .save(
            Org.create(
                TestSeq.orgCd(), "조직" + n, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1)))
        .getId();
  }

  private Long registerCandidate(Long orgId) {
    int n = SEQ.getAndIncrement();
    return agentService
        .registerCandidate(
            new NewPerson(
                "설계사" + n,
                TestSeq.rrn(),
                LocalDate.of(1988, 5, 5),
                Gender.F,
                "01000000000",
                null,
                "KR"),
            new AgentService.RegisterCommand(Channel.FC, orgId, null))
        .agentId();
  }

  /**
   * 위촉 요건까지 자격화 (라이선스·등록교육·재정보증). 종목 협회 등록은 registerAssociation이 활성화 시점에 넣는다(부록 B 6번). 보증 만기는 인자로.
   */
  private void credentialForAppoint(Long agentId, LocalDate guaranteeEnd) {
    credentialService.registerLicense(
        agentId, LicenseType.LIFE, "L-1", null, LocalDate.of(2026, 1, 1), LicenseStatus.VALID);
    credentialService.registerEducation(
        agentId, EduType.REG, "등록교육", LocalDate.of(2026, 2, 1), new BigDecimal("20"), "협회");
    credentialService.registerGuarantee(
        agentId,
        "SURETY_INS",
        MIN_GRNT,
        "보증보험사",
        "P-1",
        LocalDate.of(2026, 1, 1),
        guaranteeEnd,
        GuaranteeStatus.ACTIVE);
  }

  /**
   * 완전 자격화 + 위촉 + 협회등록 → ACTIVE. registerAssociation이 종목 협회 삽입 + 전이 + 재판정을 한 번에 하므로
   * RECRUIT_ELIG_YN=Y.
   */
  private Long activeEligibleAgent(LocalDate guaranteeEnd) {
    Long agentId = registerCandidate(newOrg());
    credentialForAppoint(agentId, guaranteeEnd);
    agentService.appoint(
        agentId,
        LocalDate.of(2026, 3, 1),
        new AgentService.ContractCommand("FC_STD", "2026-1", null, null, null));
    agentService.registerAssociation(
        agentId, LocalDate.of(2026, 3, 2), Association.LIFE_ASSOC, "L-2026");
    return agentId;
  }

  /** 보수교육 게이트를 먼 미래 기한으로 열어 둔다 (완료 2026-01-01 + 24개월 = 2028-01-01). */
  private void openContinuingEducation(Long agentId) {
    credentialService.registerEducation(
        agentId, EduType.CONTINUING, "보수교육", LocalDate.of(2026, 1, 1), new BigDecimal("12"), "협회");
  }

  private AgentStatus statusOf(Long agentId) {
    return agentRepository.findById(agentId).orElseThrow().getStatus();
  }

  private long eligibilityEvents() {
    return recorder.events().stream()
        .map(IntegrationEvent::eventType)
        .filter("agent.eligibility.changed"::equals)
        .count();
  }

  @Test
  @DisplayName("시나리오 1b: 자격 없는 후보의 위촉은 실제 미충족 사유로 422가 된다")
  void scenario1b_appointRejectedByRealRequirements() {
    Long agentId = registerCandidate(newOrg());

    assertThatThrownBy(
            () ->
                agentService.appoint(
                    agentId,
                    LocalDate.of(2026, 3, 1),
                    new AgentService.ContractCommand("FC_STD", "2026-1", null, null, null)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.errorCode().code()).isEqualTo("AGT-4221");
              assertThat(e.details())
                  .extracting(ErrorDetail::reason)
                  .contains("NO_VALID_LICENSE", "REG_EDU_NOT_COMPLETED", "NO_ACTIVE_GUARANTEE");
            });
    assertThat(statusOf(agentId)).isEqualTo(AgentStatus.CANDIDATE);
  }

  @Test
  @DisplayName("자격을 갖추면 위촉이 통과하고, 판정은 생보만 모집 가능·손보는 자격 없음으로 나온다")
  void perLineVerdict() {
    Long agentId = activeEligibleAgent(FAR);

    EligibilityResult result = eligibilityService.evaluate(agentId, LocalDate.of(2026, 3, 10));
    assertThat(result.substantiveEligible()).isTrue();
    assertThat(lineEligible(result, LicenseType.LIFE)).isTrue();
    assertThat(lineEligible(result, LicenseType.NONLIFE)).isFalse();
  }

  @Test
  @DisplayName("종합 YN은 변액 자격만 없어도 Y다 — 변액은 종목 게이트일 뿐 공통 게이트가 아니다")
  void compositeYnTrueWhenOnlyVariableMissing() {
    Long agentId = activeEligibleAgent(FAR);

    EligibilityResult result = eligibilityService.evaluate(agentId, LocalDate.of(2026, 3, 10));
    assertThat(result.substantiveEligible()).as("생보가 모집 가능하면 종합은 Y").isTrue();
    assertThat(lineEligible(result, LicenseType.VARIABLE)).as("변액은 자격 없어 불가").isFalse();
    assertThat(agentRepository.findById(agentId).orElseThrow().isRecruitEligible())
        .as("RECRUIT_ELIG_YN 캐시도 Y")
        .isTrue();
  }

  @Test
  @DisplayName("보수교육 경계는 inclusive — NEXT_DUE_DT 당일은 통과, 하루 지나면 실격")
  void continuingEducationBoundaryInclusive() {
    Long agentId = activeEligibleAgent(FAR);
    // 이수기한이 2026-06-01이 되도록 보수교육 등록 (완료일 + 24개월).
    credentialService.registerEducation(
        agentId, EduType.CONTINUING, "보수교육", LocalDate.of(2024, 6, 1), new BigDecimal("12"), "협회");

    assertThat(eligibilityService.evaluate(agentId, LocalDate.of(2026, 6, 1)).substantiveEligible())
        .as("기한 당일은 통과")
        .isTrue();
    assertThat(eligibilityService.evaluate(agentId, LocalDate.of(2026, 6, 2)).substantiveEligible())
        .as("기한 다음날은 실격")
        .isFalse();
  }

  @Test
  @DisplayName("보수교육 null 기저선 3케이스: 무이력·기한내 통과 / 무이력·도과 실격 / 이수가 기저선을 대체")
  void continuingEducationNullBaseline() {
    // 무이력: 기저선 = LAST_APPOINT_DT(2026-03-01) + 24개월 = 2028-03-01.
    Long agentId = activeEligibleAgent(FAR);

    assertThat(eligibilityService.evaluate(agentId, LocalDate.of(2027, 1, 1)).substantiveEligible())
        .as("무이력이라도 기저선 기한 내면 통과")
        .isTrue();
    assertThat(eligibilityService.evaluate(agentId, LocalDate.of(2028, 3, 2)).substantiveEligible())
        .as("무이력이고 기저선 도과면 실격")
        .isFalse();

    // 이수가 생기면 그 NEXT_DUE_DT(2026-06-01)가 기저선(2028)을 대체 → 기저선상 통과할 날에도 실격.
    credentialService.registerEducation(
        agentId, EduType.CONTINUING, "보수교육", LocalDate.of(2024, 6, 1), new BigDecimal("12"), "협회");
    assertThat(eligibilityService.evaluate(agentId, LocalDate.of(2027, 1, 1)).substantiveEligible())
        .as("이수 이력이 기저선을 대체하므로, 이수 기한(2026-06)을 넘긴 2027-01엔 실격")
        .isFalse();
  }

  @Test
  @DisplayName("복수 재정보증은 합산으로 최소금액을 비교한다")
  void guaranteeSumsAcrossPolicies() {
    Long agentId = registerCandidate(newOrg());
    credentialService.registerLicense(
        agentId, LicenseType.LIFE, "L-1", null, LocalDate.of(2026, 1, 1), LicenseStatus.VALID);
    credentialService.registerAssoc(
        agentId, Association.LIFE_ASSOC, "A-1", LocalDate.of(2026, 2, 15));
    // 보수교육 게이트를 먼 미래 기한으로 열어 두어 재정보증만 변수로 남긴다.
    openContinuingEducation(agentId);

    // 6백만 단건은 미달.
    credentialService.registerGuarantee(
        agentId,
        "SURETY_INS",
        new BigDecimal("6000000"),
        "보증사",
        "P-1",
        LocalDate.of(2026, 1, 1),
        FAR,
        GuaranteeStatus.ACTIVE);
    assertThat(
            eligibilityService.evaluate(agentId, LocalDate.of(2026, 3, 10)).substantiveEligible())
        .as("6백만 단건은 최소보증금액 미달")
        .isFalse();

    // 6백만 추가 → 합산 1천2백만 ≥ 1천만 → 충족.
    credentialService.registerGuarantee(
        agentId,
        "DEPOSIT",
        new BigDecimal("6000000"),
        "예탁",
        "P-2",
        LocalDate.of(2026, 1, 1),
        FAR,
        GuaranteeStatus.ACTIVE);
    assertThat(
            eligibilityService.evaluate(agentId, LocalDate.of(2026, 3, 10)).substantiveEligible())
        .as("합산 1천2백만은 충족")
        .isTrue();
  }

  @Test
  @DisplayName("재정보증 경계는 inclusive — END_DT 당일은 유효, 하루 지나면 무효")
  void guaranteeBoundaryInclusive() {
    Long agentId = registerCandidate(newOrg());
    credentialService.registerLicense(
        agentId, LicenseType.LIFE, "L-1", null, LocalDate.of(2026, 1, 1), LicenseStatus.VALID);
    credentialService.registerAssoc(
        agentId, Association.LIFE_ASSOC, "A-1", LocalDate.of(2026, 2, 15));
    openContinuingEducation(agentId); // 보수교육 게이트를 열어 재정보증만 변수로 둔다
    credentialService.registerGuarantee(
        agentId,
        "SURETY_INS",
        MIN_GRNT,
        "보증사",
        "P-1",
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 6, 30),
        GuaranteeStatus.ACTIVE);

    assertThat(
            eligibilityService.evaluate(agentId, LocalDate.of(2026, 6, 30)).substantiveEligible())
        .as("만기 당일은 유효")
        .isTrue();
    assertThat(eligibilityService.evaluate(agentId, LocalDate.of(2026, 7, 1)).substantiveEligible())
        .as("만기 다음날은 무효")
        .isFalse();
  }

  @Test
  @DisplayName("모집정지 제재 등록 시 자동 SUSPENDED, 제재 종료 시 자동 ACTIVE (시나리오 8 형태) + 정지해제 게이트")
  void sanctionAutoSuspendResumeGateAndAutoResume() {
    // 오늘=2026-03-01(setUp). 제재는 오늘 활성이어야 자동정지가 걸린다.
    Long agentId = activeEligibleAgent(FAR);
    assertThat(statusOf(agentId)).isEqualTo(AgentStatus.ACTIVE);
    recorder.clear();

    // 모집정지 제재(2026-01-01~무기한) → reconciler가 자동 SUSPENDED + 종합 Y→N 이벤트.
    Long sanctionId =
        credentialService.imposeSanction(
            agentId, "FSS", "RECRUIT_STOP", LocalDate.of(2026, 1, 1), null, "제재", true);
    assertThat(statusOf(agentId)).isEqualTo(AgentStatus.SUSPENDED);
    assertThat(eligibilityEvents()).as("종합 판정 Y→N으로 1건 발행").isEqualTo(1);

    // 정지해제 게이트: 제재 진행 중엔 수동 resume이 422로 거부된다.
    assertThatThrownBy(() -> agentService.resume(agentId, LocalDate.of(2026, 3, 2), "복귀 시도"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.errorCode().code()).isEqualTo("AGT-4222");
              assertThat(e.details()).extracting(ErrorDetail::reason).contains("RECRUIT_BLOCKED");
            });
    assertThat(statusOf(agentId)).isEqualTo(AgentStatus.SUSPENDED);

    // 제재를 3/15자로 종료. 종료일까지는(inclusive) 여전히 정지 — 오늘(3/1) 기준 재판정은 아직 SUSPENDED.
    credentialService.liftSanction(sanctionId, LocalDate.of(2026, 3, 15));
    assertThat(statusOf(agentId)).isEqualTo(AgentStatus.SUSPENDED);

    // 종료일 다음날로 넘어가 재판정하면 제재가 풀려 자동 ACTIVE 복귀.
    clock.setDate(LocalDate.of(2026, 3, 16));
    reconciler.reconcile(agentId);
    assertThat(statusOf(agentId)).isEqualTo(AgentStatus.ACTIVE);
  }

  @Test
  @DisplayName("시나리오 3: 재정보증이 만료돼 정지된 설계사가 보증 갱신 등록으로 자동 재활성화된다")
  void scenario3_guaranteeRenewalReactivates() {
    // 만기가 임박한 보증으로 활성화한 뒤, 시계를 만기 이후로 옮기고 재판정 → 자동 SUSPENDED.
    Long agentId = activeEligibleAgent(LocalDate.of(2026, 6, 30));
    clock.setDate(LocalDate.of(2026, 7, 1)); // 보증 만기 다음날
    reconciler.reconcile(agentId);
    assertThat(statusOf(agentId)).as("보증 만료로 자동 정지").isEqualTo(AgentStatus.SUSPENDED);

    // 보증 갱신 등록(쓰기) → reconciler가 자동 ACTIVE 복귀.
    credentialService.registerGuarantee(
        agentId,
        "SURETY_INS",
        MIN_GRNT,
        "보증사",
        "P-2",
        LocalDate.of(2026, 7, 1),
        FAR,
        GuaranteeStatus.ACTIVE);
    assertThat(statusOf(agentId)).as("보증 갱신으로 자동 재활성화").isEqualTo(AgentStatus.ACTIVE);
  }

  @Test
  @DisplayName("불변식: 정상 위촉 워크플로 종료 시 appointed와 eligibility.changed(Y)가 함께 나가고 스냅샷 YN=Y다")
  void activationSnapshotMatchesEvaluate() {
    Long agentId = registerCandidate(newOrg());
    credentialForAppoint(agentId, FAR);
    agentService.appoint(
        agentId,
        LocalDate.of(2026, 3, 1),
        new AgentService.ContractCommand("FC_STD", "2026-1", null, null, null));
    recorder.clear();

    agentService.registerAssociation(
        agentId, LocalDate.of(2026, 3, 2), Association.LIFE_ASSOC, "L-1");

    assertThat(statusOf(agentId)).isEqualTo(AgentStatus.ACTIVE);
    assertThat(agentRepository.findById(agentId).orElseThrow().isRecruitEligible())
        .as("워크플로 종료 시 스냅샷 YN = evaluate() 결과 (낡은 N 창구 없음)")
        .isTrue();
    assertThat(recorder.events())
        .extracting(IntegrationEvent::eventType)
        .contains("agent.appointed", "agent.eligibility.changed");
  }

  @Test
  @DisplayName("불변식 역방향: 요건검증 후 보증이 만료되면 협회등록으로 활성화돼도 즉시 SUSPENDED가 된다 (정답)")
  void guaranteeExpiredBetweenAppointAndActivationYieldsSuspended() {
    clock.setDate(LocalDate.of(2026, 2, 1));
    Long agentId = registerCandidate(newOrg());
    // 위촉 시점(2/1)엔 유효하지만 2/15에 만료되는 보증.
    credentialForAppoint(agentId, LocalDate.of(2026, 2, 15));
    agentService.appoint(
        agentId,
        LocalDate.of(2026, 2, 1),
        new AgentService.ContractCommand("FC_STD", "2026-1", null, null, null));

    // 며칠 흘러 보증이 만료된 뒤 협회 등록번호 수신.
    clock.setDate(LocalDate.of(2026, 3, 1));
    agentService.registerAssociation(
        agentId, LocalDate.of(2026, 3, 1), Association.LIFE_ASSOC, "L-1");

    // 활성화 워크플로 끝의 재판정이 보증 만료를 보고 즉시 정지시킨다 — 버그가 아니라 정답.
    assertThat(statusOf(agentId)).isEqualTo(AgentStatus.SUSPENDED);
    assertThat(agentRepository.findById(agentId).orElseThrow().isRecruitEligible()).isFalse();
  }

  @Test
  @DisplayName("agent.eligibility.changed는 종합 판정이 실제로 바뀔 때만 발행된다 (쓰기마다가 아니라)")
  void eligibilityChangedOnlyOnCompositeChange() {
    Long agentId = activeEligibleAgent(FAR);
    recorder.clear();

    // 종합 판정을 바꾸지 않는 재판정 반복 — 이벤트가 나가면 안 된다.
    reconciler.reconcile(agentId);
    reconciler.reconcile(agentId);
    assertThat(eligibilityEvents()).as("Y→Y 반복은 무발행").isZero();

    // 종합을 바꾸는 변화(라이선스 말소) → 정확히 1건. 자격화 헬퍼가 생보 1건만 만든다.
    Long licenseId = licenseRepository.findByAgentId(agentId).get(0).getId();
    credentialService.changeLicenseStatus(licenseId, LicenseStatus.REVOKED);
    assertThat(eligibilityEvents()).as("Y→N 변화로 1건").isEqualTo(1);
  }

  private boolean lineEligible(EligibilityResult result, LicenseType line) {
    return result.lines().stream()
        .filter(l -> l.line() == line)
        .findFirst()
        .orElseThrow()
        .eligible();
  }
}
