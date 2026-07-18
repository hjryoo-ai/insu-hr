package com.portfolio.insuhr.batch.support;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;

/**
 * 배치 통합 테스트 공통 베이스 (설계서 8, 12장).
 *
 * <p>실제 Oracle + Flyway로 돈다. 배치는 운영에선 validate 전용이지만(4.2), 테스트는 {@code insuhr.flyway.mode}를
 * migrate로 바꿔 빈 컨테이너에 스키마를 구성한다(FlywayValidateOnlyConfig는 값이 "validate"일 때만 활성).
 *
 * <p>{@code spring.batch.job.enabled=false}로 시동 시 자동 잡 실행을 끄고, {@link #runJob}이 {@link
 * JobOperator}로 결정적으로 잡을 돌린다. 컨테이너는 클래스 간 공유되므로 업무 테이블을 매 테스트 초기화한다.
 *
 * <p>잡 파라미터는 규약대로 {@code targetDate}(업무 기준일)를 넘긴다. 같은 targetDate 재실행을 위해 {@code run.id}를 유니크하게 붙여
 * 별개 JobInstance를 연다(멱등성 테스트의 배선).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"insuhr.flyway.mode=migrate", "spring.batch.job.enabled=false"})
@Tag("integration")
public abstract class AbstractBatchIntegrationTest {

  private static final OracleContainer ORACLE =
      new OracleContainer("gvenzl/oracle-free:23-slim")
          .withUsername("insuhr")
          .withPassword("insuhr")
          .withStartupTimeout(Duration.ofMinutes(5));

  private static final AtomicLong RUN_ID = new AtomicLong(1);

  static {
    ORACLE.start();
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
    registry.add("spring.datasource.username", ORACLE::getUsername);
    registry.add("spring.datasource.password", ORACLE::getPassword);
  }

  @Autowired protected JdbcClient jdbcClient;
  @Autowired protected JobOperator jobOperator;

  /** 업무 테이블 초기화(자식 FK부터). 배치 메타테이블은 건드리지 않는다 — 잡 실행 이력은 누적돼도 무해하다. */
  @BeforeEach
  void cleanBusinessTables() {
    List<String> tables =
        List.of(
            "TB_NOTICE_QUEUE",
            "TB_DQ_FINDING",
            "TB_IF_SEND_LOG",
            "TB_IF_DELIVERY",
            "TB_IF_SUBSCRIBER",
            "TB_IF_OUTBOX",
            "TB_IF_CHANGE_LOG",
            "TB_MISSELL_CASE",
            "TB_AGENT_SANCTION",
            "TB_FIN_GUARANTEE",
            "TB_AGENT_EDU",
            "TB_CROSS_SELL_REG",
            "TB_ASSOC_REG",
            "TB_AGENT_LICENSE",
            "TB_AGENT_APPOINT_HIST",
            "TB_EMP_APPOINT",
            "TB_AGENT",
            "TB_LEAVE_REQ",
            "TB_LEAVE_GRANT",
            "TB_EMP",
            "TB_ORG_HIST",
            "TB_PERSON",
            "TB_ORG");
    for (String table : tables) {
      jdbcClient.sql("DELETE FROM " + table).update();
    }
  }

  /** {@code targetDate}로 잡을 동기 실행하고 종료 실행을 돌려준다(유니크 run.id로 동일 targetDate 재실행 허용). */
  protected JobExecution runJob(Job job, LocalDate targetDate) throws Exception {
    JobParameters params =
        new JobParametersBuilder()
            .addString("targetDate", targetDate.toString())
            .addLong("run.id", RUN_ID.getAndIncrement())
            .toJobParameters();
    return jobOperator.start(job, params);
  }

  protected static boolean isCompleted(JobExecution execution) {
    return execution.getStatus() == BatchStatus.COMPLETED;
  }

  /** 테스트 간 겹치지 않는 접미사(컨테이너 공유로 데이터 누적). */
  protected String uniq() {
    return Long.toString(System.nanoTime() % 100_000_000L);
  }

  // ── SQL 시드 헬퍼 (도메인 서비스가 api에 있어 배치 테스트에선 SQL로 직접 심는다) ──────────────

  protected long seedOrg() {
    String cd = "O" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_ORG (ORG_CD, ORG_NM, ORG_TYPE_CD, ORG_LVL, VALID_FROM_DT, CREATED_BY)"
                + " VALUES (:cd, :nm, 'BRANCH', 1, DATE '2020-01-01', 'BATCH-TEST')")
        .param("cd", cd)
        .param("nm", "조직-" + cd)
        .update();
    return jdbcClient
        .sql("SELECT ORG_ID FROM TB_ORG WHERE ORG_CD = :cd")
        .param("cd", cd)
        .query(Long.class)
        .single();
  }

  protected long seedPerson() {
    String hash = "h" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_PERSON (PERSON_NM, RRN_ENC, RRN_HASH, BIRTH_DT, GENDER_CD,"
                + " NATIONALITY_CD, CREATED_BY)"
                + " VALUES ('인물', 'v1:seed', :hash, DATE '1988-05-05', 'F', 'KR', 'BATCH-TEST')")
        .param("hash", hash)
        .update();
    return jdbcClient
        .sql("SELECT PERSON_ID FROM TB_PERSON WHERE RRN_HASH = :hash")
        .param("hash", hash)
        .query(Long.class)
        .single();
  }

  protected long seedEmp(long personId, long orgId) {
    String empNo = "E" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_EMP (PERSON_ID, EMP_NO, EMP_TYPE_CD, ORG_ID, HIRE_DT, EMP_STATUS_CD,"
                + " CREATED_BY)"
                + " VALUES (:pid, :no, 'REGULAR', :org, DATE '2025-01-01', 'ACTIVE', 'BATCH-TEST')")
        .param("pid", personId)
        .param("no", empNo)
        .param("org", orgId)
        .update();
    return jdbcClient
        .sql("SELECT EMP_ID FROM TB_EMP WHERE EMP_NO = :no")
        .param("no", empNo)
        .query(Long.class)
        .single();
  }

  /** 발령일이 도래한(=targetDate) CONFIRMED 전보 발령을 심는다 — futureAppointApplyJob의 대상. */
  protected void seedConfirmedTransfer(long empId, LocalDate appointDt, long targetOrgId) {
    jdbcClient
        .sql(
            "INSERT INTO TB_EMP_APPOINT (EMP_ID, APPOINT_TYPE_CD, APPOINT_DT, ORG_ID, EMP_STATUS_CD,"
                + " DOC_STATUS_CD, CREATED_BY)"
                + " VALUES (:emp, 'TRANSFER', :dt, :org, 'ACTIVE', 'CONFIRMED', 'BATCH-TEST')")
        .param("emp", empId)
        .param("dt", appointDt)
        .param("org", targetOrgId)
        .update();
  }

  /**
   * ACTIVE·모집자격 Y인 생보 설계사를 심는다 — 생보 게이트(자격/협회/보증)는 모두 열고, 보수교육 기한만 {@code ceNextDueDt}로 둬 그 경계 넘김이
   * 유일한 실격 변수가 되게 한다.
   */
  protected long seedActiveEligibleLifeAgent(long personId, long orgId, LocalDate ceNextDueDt) {
    return seedActiveEligibleLifeAgent(personId, orgId, ceNextDueDt, LocalDate.of(2035, 1, 1));
  }

  /** 위와 같되 유일한 재정보증의 만료일을 지정한다 — guaranteeExpiryJob 테스트가 만료 경계를 조작하려면 필요. */
  protected long seedActiveEligibleLifeAgent(
      long personId, long orgId, LocalDate ceNextDueDt, LocalDate grntEndDt) {
    String cd = "A" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_AGENT (PERSON_ID, AGENT_CD, CHANNEL_CD, ORG_ID, AGENT_STATUS_CD,"
                + " FIRST_APPOINT_DT, LAST_APPOINT_DT, RECRUIT_ELIG_YN, CREATED_BY)"
                + " VALUES (:pid, :cd, 'FC', :org, 'ACTIVE', DATE '2026-01-01', DATE '2026-01-01',"
                + " 'Y', 'BATCH-TEST')")
        .param("pid", personId)
        .param("cd", cd)
        .param("org", orgId)
        .update();
    long agentId =
        jdbcClient
            .sql("SELECT AGENT_ID FROM TB_AGENT WHERE AGENT_CD = :cd")
            .param("cd", cd)
            .query(Long.class)
            .single();

    jdbcClient
        .sql(
            "INSERT INTO TB_AGENT_LICENSE (AGENT_ID, LICENSE_TYPE_CD, LICENSE_NO, REG_DT, STATUS_CD,"
                + " CREATED_BY) VALUES (:a, 'LIFE', 'L-1', DATE '2026-01-01', 'VALID', 'BATCH-TEST')")
        .param("a", agentId)
        .update();
    jdbcClient
        .sql(
            "INSERT INTO TB_ASSOC_REG (AGENT_ID, ASSOC_CD, ASSOC_REG_NO, REG_DT, STATUS_CD,"
                + " CREATED_BY) VALUES (:a, 'LIFE_ASSOC', 'A-1', DATE '2026-01-01', 'REGISTERED',"
                + " 'BATCH-TEST')")
        .param("a", agentId)
        .update();
    jdbcClient
        .sql(
            "INSERT INTO TB_FIN_GUARANTEE (AGENT_ID, GRNT_TYPE_CD, GRNT_AMT, ISSUER_NM, POLICY_NO,"
                + " START_DT, END_DT, STATUS_CD, CREATED_BY)"
                + " VALUES (:a, 'SURETY_INS', 10000000, '보증사', 'P-1', DATE '2026-01-01',"
                + " :grntEnd, 'ACTIVE', 'BATCH-TEST')")
        .param("a", agentId)
        .param("grntEnd", grntEndDt)
        .update();
    jdbcClient
        .sql(
            "INSERT INTO TB_AGENT_EDU (AGENT_ID, EDU_TYPE_CD, EDU_NM, COMPLETE_DT, EDU_HOURS,"
                + " NEXT_DUE_DT, CREATED_BY)"
                + " VALUES (:a, 'CONTINUING', '보수교육', DATE '2024-06-01', 12, :due, 'BATCH-TEST')")
        .param("a", agentId)
        .param("due", ceNextDueDt)
        .update();
    return agentId;
  }

  protected String agentStatus(long agentId) {
    return jdbcClient
        .sql("SELECT AGENT_STATUS_CD FROM TB_AGENT WHERE AGENT_ID = :id")
        .param("id", agentId)
        .query(String.class)
        .single();
  }

  protected String recruitEligYn(long agentId) {
    return jdbcClient
        .sql("SELECT RECRUIT_ELIG_YN FROM TB_AGENT WHERE AGENT_ID = :id")
        .param("id", agentId)
        .query(String.class)
        .single();
  }

  protected long orgOf(long empId) {
    return jdbcClient
        .sql("SELECT ORG_ID FROM TB_EMP WHERE EMP_ID = :id")
        .param("id", empId)
        .query(Long.class)
        .single();
  }

  protected int outboxCount(String aggType, long aggId, String eventType) {
    return jdbcClient
        .sql(
            "SELECT COUNT(*) FROM TB_IF_OUTBOX WHERE AGG_TYPE = :t AND AGG_ID = :id"
                + " AND EVENT_TYPE = :et")
        .param("t", aggType)
        .param("id", aggId)
        .param("et", eventType)
        .query(Integer.class)
        .single();
  }

  // ── Phase 7 후속 잡용 시드/검증 헬퍼 ──────────────────────────────────────

  /** 만료일·상태를 지정한 재정보증 한 건을 심고 GRNT_ID를 돌려준다. */
  protected long seedGuarantee(long agentId, LocalDate endDt, String statusCd) {
    String policyNo = "PN" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_FIN_GUARANTEE (AGENT_ID, GRNT_TYPE_CD, GRNT_AMT, ISSUER_NM, POLICY_NO,"
                + " START_DT, END_DT, STATUS_CD, CREATED_BY)"
                + " VALUES (:a, 'SURETY_INS', 10000000, '보증사', :pno, DATE '2020-01-01',"
                + " :end, :st, 'BATCH-TEST')")
        .param("a", agentId)
        .param("pno", policyNo)
        .param("end", endDt)
        .param("st", statusCd)
        .update();
    return jdbcClient
        .sql("SELECT GRNT_ID FROM TB_FIN_GUARANTEE WHERE POLICY_NO = :pno")
        .param("pno", policyNo)
        .query(Long.class)
        .single();
  }

  protected String guaranteeStatus(long grntId) {
    return jdbcClient
        .sql("SELECT STATUS_CD FROM TB_FIN_GUARANTEE WHERE GRNT_ID = :id")
        .param("id", grntId)
        .query(String.class)
        .single();
  }

  /** 어떤 대상의 특정 유형 알림 총 건수(마일스톤 무관). */
  protected int noticeCount(String noticeTypeCd, long targetId) {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM TB_NOTICE_QUEUE WHERE NOTICE_TYPE_CD = :t AND TARGET_ID = :id")
        .param("t", noticeTypeCd)
        .param("id", targetId)
        .query(Integer.class)
        .single();
  }

  /** 특정 (유형,대상,마일스톤) 알림 건수 — dedup 검증용. */
  protected int noticeCount(String noticeTypeCd, long targetId, String milestoneCd) {
    return jdbcClient
        .sql(
            "SELECT COUNT(*) FROM TB_NOTICE_QUEUE"
                + " WHERE NOTICE_TYPE_CD = :t AND TARGET_ID = :id AND MILESTONE_CD = :m")
        .param("t", noticeTypeCd)
        .param("id", targetId)
        .param("m", milestoneCd)
        .query(Integer.class)
        .single();
  }

  protected int findingCount(String ruleCd) {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM TB_DQ_FINDING WHERE RULE_CD = :r")
        .param("r", ruleCd)
        .query(Integer.class)
        .single();
  }

  protected int findingCount(String ruleCd, long targetId) {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM TB_DQ_FINDING WHERE RULE_CD = :r AND TARGET_ID = :id")
        .param("r", ruleCd)
        .param("id", targetId)
        .query(Integer.class)
        .single();
  }

  /** 입사일·재직상태를 지정한 임직원을 심는다. */
  protected long seedEmpHired(long personId, long orgId, LocalDate hireDt, String statusCd) {
    String empNo = "E" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_EMP (PERSON_ID, EMP_NO, EMP_TYPE_CD, ORG_ID, HIRE_DT, EMP_STATUS_CD,"
                + " CREATED_BY) VALUES (:pid, :no, 'REGULAR', :org, :hire, :st, 'BATCH-TEST')")
        .param("pid", personId)
        .param("no", empNo)
        .param("org", orgId)
        .param("hire", hireDt)
        .param("st", statusCd)
        .update();
    return jdbcClient
        .sql("SELECT EMP_ID FROM TB_EMP WHERE EMP_NO = :no")
        .param("no", empNo)
        .query(Long.class)
        .single();
  }

  /** USE_YN='N'(폐지) 조직을 심는다 — dataQualityJob EMP_ORG_ABOLISHED 룰용. */
  protected long seedAbolishedOrg() {
    String cd = "O" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_ORG (ORG_CD, ORG_NM, ORG_TYPE_CD, ORG_LVL, VALID_FROM_DT, USE_YN,"
                + " CREATED_BY)"
                + " VALUES (:cd, :nm, 'BRANCH', 1, DATE '2020-01-01', 'N', 'BATCH-TEST')")
        .param("cd", cd)
        .param("nm", "폐지조직-" + cd)
        .update();
    return jdbcClient
        .sql("SELECT ORG_ID FROM TB_ORG WHERE ORG_CD = :cd")
        .param("cd", cd)
        .query(Long.class)
        .single();
  }

  /** 자격·협회 없이 ACTIVE인 설계사를 심는다 — dataQuality/licenseValidity 룰의 최소 대상. */
  protected long seedBareActiveAgent(long personId, long orgId) {
    String cd = "A" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_AGENT (PERSON_ID, AGENT_CD, CHANNEL_CD, ORG_ID, AGENT_STATUS_CD,"
                + " FIRST_APPOINT_DT, LAST_APPOINT_DT, RECRUIT_ELIG_YN, CREATED_BY)"
                + " VALUES (:pid, :cd, 'FC', :org, 'ACTIVE', DATE '2026-01-01', DATE '2026-01-01',"
                + " 'N', 'BATCH-TEST')")
        .param("pid", personId)
        .param("cd", cd)
        .param("org", orgId)
        .update();
    return jdbcClient
        .sql("SELECT AGENT_ID FROM TB_AGENT WHERE AGENT_CD = :cd")
        .param("cd", cd)
        .query(Long.class)
        .single();
  }

  protected void seedLicense(long agentId, String typeCd, String statusCd) {
    jdbcClient
        .sql(
            "INSERT INTO TB_AGENT_LICENSE (AGENT_ID, LICENSE_TYPE_CD, LICENSE_NO, REG_DT, STATUS_CD,"
                + " CREATED_BY) VALUES (:a, :type, :no, DATE '2026-01-01', :st, 'BATCH-TEST')")
        .param("a", agentId)
        .param("type", typeCd)
        .param("no", "L" + uniq())
        .param("st", statusCd)
        .update();
  }

  protected void seedAssoc(long agentId, String assocCd, String statusCd) {
    jdbcClient
        .sql(
            "INSERT INTO TB_ASSOC_REG (AGENT_ID, ASSOC_CD, ASSOC_REG_NO, REG_DT, STATUS_CD,"
                + " CREATED_BY) VALUES (:a, :assoc, :no, DATE '2026-01-01', :st, 'BATCH-TEST')")
        .param("a", agentId)
        .param("assoc", assocCd)
        .param("no", "AR" + uniq())
        .param("st", statusCd)
        .update();
  }

  /** 그 해 부여된 연차 일수(없으면 -1). */
  protected int leaveGrantDays(long empId, int yearNo) {
    return jdbcClient
        .sql(
            "SELECT NVL((SELECT GRANT_DAYS FROM TB_LEAVE_GRANT"
                + " WHERE EMP_ID = :e AND YEAR_NO = :y), -1) FROM DUAL")
        .param("e", empId)
        .param("y", yearNo)
        .query(Integer.class)
        .single();
  }

  protected int leaveGrantCount(long empId, int yearNo) {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM TB_LEAVE_GRANT WHERE EMP_ID = :e AND YEAR_NO = :y")
        .param("e", empId)
        .param("y", yearNo)
        .query(Integer.class)
        .single();
  }

  /** WEBHOOK 구독자를 심고 SUBSCRIBER_ID를 돌려준다 — 전달 레코드 FK용. */
  protected long seedSubscriber() {
    String systemCd = "SYS" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_IF_SUBSCRIBER (SYSTEM_CD, SYSTEM_NM, DELIVERY_TYPE_CD, ENDPOINT_URL,"
                + " USE_YN, CREATED_BY)"
                + " VALUES (:cd, :nm, 'WEBHOOK', 'http://localhost/hook', 'Y', 'BATCH-TEST')")
        .param("cd", systemCd)
        .param("nm", "구독-" + systemCd)
        .update();
    return jdbcClient
        .sql("SELECT SUBSCRIBER_ID FROM TB_IF_SUBSCRIBER WHERE SYSTEM_CD = :cd")
        .param("cd", systemCd)
        .query(Long.class)
        .single();
  }

  /** OCCURRED_AT을 며칠 전으로 지정한 Outbox 이벤트를 심고 EVENT_ID를 돌려준다. */
  protected long seedOutbox(
      String aggType, long aggId, String eventType, String statusCd, int occurredDaysAgo) {
    String uuid = "u" + uniq();
    jdbcClient
        .sql(
            "INSERT INTO TB_IF_OUTBOX (EVENT_UUID, AGG_TYPE, AGG_ID, EVENT_TYPE, PAYLOAD, STATUS_CD,"
                + " OCCURRED_AT)"
                + " VALUES (:uuid, :t, :id, :et, '{}', :st,"
                + " SYS_EXTRACT_UTC(SYSTIMESTAMP) - NUMTODSINTERVAL(:days, 'DAY'))")
        .param("uuid", uuid)
        .param("t", aggType)
        .param("id", aggId)
        .param("et", eventType)
        .param("st", statusCd)
        .param("days", occurredDaysAgo)
        .update();
    return jdbcClient
        .sql("SELECT EVENT_ID FROM TB_IF_OUTBOX WHERE EVENT_UUID = :uuid")
        .param("uuid", uuid)
        .query(Long.class)
        .single();
  }

  /** CREATED_AT을 며칠 전으로 지정한 전달 레코드를 심고 DELIVERY_ID를 돌려준다. */
  protected long seedDelivery(
      long eventId,
      long subscriberId,
      String aggType,
      long aggId,
      String statusCd,
      int createdDaysAgo) {
    jdbcClient
        .sql(
            "INSERT INTO TB_IF_DELIVERY (EVENT_ID, SUBSCRIBER_ID, AGG_TYPE, AGG_ID, STATUS_CD,"
                + " CREATED_AT)"
                + " VALUES (:e, :s, :t, :id, :st,"
                + " SYS_EXTRACT_UTC(SYSTIMESTAMP) - NUMTODSINTERVAL(:days, 'DAY'))")
        .param("e", eventId)
        .param("s", subscriberId)
        .param("t", aggType)
        .param("id", aggId)
        .param("st", statusCd)
        .param("days", createdDaysAgo)
        .update();
    return jdbcClient
        .sql(
            "SELECT MAX(DELIVERY_ID) FROM TB_IF_DELIVERY WHERE EVENT_ID = :e AND SUBSCRIBER_ID = :s")
        .param("e", eventId)
        .param("s", subscriberId)
        .query(Long.class)
        .single();
  }
}
