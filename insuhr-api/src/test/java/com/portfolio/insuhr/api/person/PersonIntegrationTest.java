package com.portfolio.insuhr.api.person;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.common.crypto.PepperedHasher;
import com.portfolio.insuhr.domain.audit.PrivacyAccessLog;
import com.portfolio.insuhr.domain.audit.PrivacyAccessLogRepository;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import com.portfolio.insuhr.domain.person.Person;
import com.portfolio.insuhr.domain.person.PersonRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 인물 등록·중복차단·복호화 (설계서 5.2, 10.2).
 *
 * <p>시나리오 8번(동일 주민번호로 이중 역할 → PERSON 1건 유지)의 기반 (설계서 12장).
 */
class PersonIntegrationTest extends AbstractIntegrationTest {

  private static final AtomicInteger SEQ = new AtomicInteger(1);

  @Autowired PersonService personService;
  @Autowired PersonRepository personRepository;
  @Autowired PrivacyAccessLogRepository accessLogRepository;
  @Autowired JdbcClient jdbcClient;
  @Autowired PepperedHasher rrnHasher;
  @Autowired com.portfolio.insuhr.domain.auth.UserAccountRepository userRepository;

  /** 접근로그의 USER_ID는 TB_USER를 참조하므로 실제 계정이 필요하다. */
  private Long anyUserId() {
    return userRepository
        .save(
            com.portfolio.insuhr.domain.auth.UserAccount.ofHuman(
                "person-test-" + SEQ.getAndIncrement(), "{bcrypt}dummy", null))
        .getId();
  }

  /** 테스트 간 주민번호가 겹치지 않게 한다 — 컨테이너를 클래스 간에 공유한다. */
  private String uniqueRrn() {
    return "900101-" + String.format("%07d", SEQ.getAndIncrement());
  }

  private NewPerson newPerson(String rrn, String name) {
    return new NewPerson(name, rrn, LocalDate.of(1990, 1, 1), Gender.M, "01012341234", null, "KR");
  }

  @Test
  @DisplayName("인물을 등록하면 주민번호는 암호문과 해시로 나뉘어 저장되고 원문은 남지 않는다")
  void registerStoresEncryptedAndHashedRrn() {
    String rrn = uniqueRrn();
    Long personId = personService.register(newPerson(rrn, "김민수")).personId();

    RrnColumns row =
        jdbcClient
            .sql(
                "SELECT RRN_ENC AS rrnEnc, RRN_HASH AS rrnHash FROM TB_PERSON WHERE PERSON_ID = :id")
            .param("id", personId)
            .query(RrnColumns.class)
            .single();

    // 암호문에 원문이 남으면 암호화가 무의미하다
    assertThat(row.rrnEnc()).doesNotContain(rrn).doesNotContain("900101");
    // 키버전 프리픽스 (설계서 10.3 — 키 회전 가능 구조)
    assertThat(row.rrnEnc()).startsWith("v1:");
    assertThat(row.rrnHash()).hasSize(64).doesNotContain("900101");
  }

  @Test
  @DisplayName("휴대폰 마스킹 표시값이 쓰기 시점에 저장된다 — 목록에서 복호화하지 않기 위해")
  void storesMaskedMobileAtWriteTime() {
    Long personId = personService.register(newPerson(uniqueRrn(), "김민수")).personId();

    // 설계서 10.2 v1.2: 목록 20건 = 복호화 20회를 피하려 쓰기 시점에 계산해 저장한다
    String masked =
        jdbcClient
            .sql("SELECT MOBILE_MASKED FROM TB_PERSON WHERE PERSON_ID = :id")
            .param("id", personId)
            .query(String.class)
            .single();
    assertThat(masked).isEqualTo("010-****-1234");

    // 암호문과 마스킹 값이 같은 원문에서 함께 파생됐는지 — 엔티티가 강제하는 불변식
    Person person = personRepository.findById(personId).orElseThrow();
    assertThat(person.getMobileMasked()).isEqualTo("010-****-1234");
  }

  @Test
  @DisplayName("같은 주민번호로 다시 등록하면 새 인물이 생기지 않고 기존 인물이 나온다")
  void registeringSameRrnReusesExistingPerson() {
    String rrn = uniqueRrn();

    PersonService.Registration first = personService.register(newPerson(rrn, "김민수"));
    PersonService.Registration second = personService.register(newPerson(rrn, "김민수"));

    assertThat(first.created()).isTrue();
    assertThat(second.created()).isFalse();
    // 설계서 5.2: 인물은 하나. 직원 퇴사 후 설계사 위촉 같은 케이스에서 역할만 추가된다
    assertThat(second.personId()).isEqualTo(first.personId());
    assertThat(countPersonsWithRrn(rrn)).isEqualTo(1);
  }

  @Test
  @DisplayName("동시에 같은 주민번호를 등록해도 인물은 1건만 생긴다 (유니크 제약이 방어선)")
  void concurrentRegistrationCreatesSinglePerson() throws Exception {
    String rrn = uniqueRrn();
    int threads = 8;

    // 순차 호출로는 이 레이스가 드러나지 않는다. 검사-후-삽입에 의존했다면
    // 여러 스레드가 동시에 "없음"을 보고 전부 INSERT를 시도해 중복이 생긴다.
    CyclicBarrier startTogether = new CyclicBarrier(threads);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Callable<Long>> tasks =
          java.util.stream.IntStream.range(0, threads)
              .<Callable<Long>>mapToObj(
                  i ->
                      () -> {
                        startTogether.await(10, TimeUnit.SECONDS);
                        return personService.register(newPerson(rrn, "동시등록")).personId();
                      })
              .toList();

      List<Future<Long>> futures = pool.invokeAll(tasks);

      Set<Long> personIds = ConcurrentHashMap.newKeySet();
      int failures = 0;
      for (Future<Long> future : futures) {
        try {
          personIds.add(future.get(30, TimeUnit.SECONDS));
        } catch (Exception e) {
          failures++;
        }
      }

      assertThat(failures).as("동시 등록은 예외 없이 기존 인물로 전환돼야 한다").isZero();
      assertThat(personIds).as("모든 요청이 같은 인물을 가리켜야 한다").hasSize(1);
      assertThat(countPersonsWithRrn(rrn)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("복호화하면 원문이 나오고 접근로그가 같은 트랜잭션에 남는다")
  void decryptLeavesAccessLog() {
    String rrn = uniqueRrn();
    Long personId = personService.register(newPerson(rrn, "김민수")).personId();

    Long actorId = anyUserId();
    String decrypted =
        personService.decryptRrn(personId, actorId, "보험금 지급 심사", "POST /test", "127.0.0.1");

    assertThat(decrypted).isEqualTo(rrn);

    // 설계서 10.2: 복호화는 접근로그 필수. 기록 없으면 열람 없음(10.1.1)
    List<PrivacyAccessLog> logs =
        accessLogRepository.findByTargetPersonIdOrderByAccessAtDesc(personId);
    assertThat(logs).hasSize(1);
    assertThat(logs.get(0).getAccessTypeCd()).isEqualTo("DECRYPT");
    assertThat(logs.get(0).getPurposeTxt()).isEqualTo("보험금 지급 심사");
    assertThat(logs.get(0).getClientIp()).isEqualTo("127.0.0.1");
  }

  @Test
  @DisplayName("사유 없이 복호화하면 거부되고 접근로그도 남지 않는다")
  void rejectsDecryptWithoutPurpose() {
    Long personId = personService.register(newPerson(uniqueRrn(), "김민수")).personId();

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> personService.decryptRrn(personId, anyUserId(), "  ", "POST /test", "127.0.0.1"))
        .isInstanceOf(com.portfolio.insuhr.common.exception.BusinessException.class)
        .hasMessageContaining("사유");

    assertThat(accessLogRepository.findByTargetPersonIdOrderByAccessAtDesc(personId)).isEmpty();
  }

  @Test
  @DisplayName("감사 컬럼이 채워진다 — 인증 컨텍스트가 없으면 SYSTEM 폴백")
  void auditColumnsAreFilledWithSystemFallback() {
    Long personId = personService.register(newPerson(uniqueRrn(), "김민수")).personId();

    String createdBy =
        jdbcClient
            .sql("SELECT CREATED_BY FROM TB_PERSON WHERE PERSON_ID = :id")
            .param("id", personId)
            .query(String.class)
            .single();

    // 이 테스트는 HTTP를 타지 않아 SecurityContext가 비어 있다.
    // 폴백이 없으면 CREATED_BY NOT NULL 제약에 걸린다 — Phase 7 배치가 밟을 경로다(설계서 13.2 v1.2).
    assertThat(createdBy).isEqualTo("SYSTEM-insuhr-api");
  }

  /** 해당 주민번호로 실제 몇 행이 들어갔는지 DB에서 직접 센다. */
  private int countPersonsWithRrn(String rrn) {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM TB_PERSON WHERE RRN_HASH = :hash")
        .param("hash", rrnHasher.hash(rrn))
        .query(Integer.class)
        .single();
  }

  record RrnColumns(String rrnEnc, String rrnHash) {}
}
