package com.portfolio.insuhr.api.person;

import com.portfolio.insuhr.common.crypto.AesGcmCipher;
import com.portfolio.insuhr.common.crypto.PepperedHasher;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.audit.PrivacyAccessLog;
import com.portfolio.insuhr.domain.audit.PrivacyAccessLogRepository;
import com.portfolio.insuhr.domain.audit.PrivacyAccessType;
import com.portfolio.insuhr.domain.person.NewPerson;
import com.portfolio.insuhr.domain.person.Person;
import com.portfolio.insuhr.domain.person.PersonErrorCode;
import com.portfolio.insuhr.domain.person.PersonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 인물 유스케이스 (설계서 7.2 PER, 5.2).
 *
 * <p>인물은 하나, 역할은 여럿(설계서 1.4). 같은 사람이 두 번 등록되면 이 모델 전체가 무너지므로 중복 차단이 이 서비스의 핵심 책임이다.
 */
@Service
public class PersonService {

  private static final Logger log = LoggerFactory.getLogger(PersonService.class);

  private final PersonRepository personRepository;
  private final PrivacyAccessLogRepository accessLogRepository;
  private final PersonInserter personInserter;
  private final AesGcmCipher cipher;
  private final PepperedHasher rrnHasher;

  public PersonService(
      PersonRepository personRepository,
      PrivacyAccessLogRepository accessLogRepository,
      PersonInserter personInserter,
      AesGcmCipher cipher,
      PepperedHasher rrnHasher) {
    this.personRepository = personRepository;
    this.accessLogRepository = accessLogRepository;
    this.personInserter = personInserter;
    this.cipher = cipher;
    this.rrnHasher = rrnHasher;
  }

  /**
   * 인물 등록 — 이미 있으면 기존 인물을 돌려준다 (설계서 5.2 v1.2).
   *
   * <p><b>검사-후-삽입에 의존하지 않는다.</b> "없으면 INSERT"는 두 요청이 동시에 "없음"을 확인하고 둘 다 INSERT 하는 창이 열린다. 실제 방어선은
   * {@code UQ_PERSON_RRN} 유니크 제약이고, 여기서는 제약 위반을 잡아 기존 인물 재사용으로 전환한다. 이 전환은 예외 처리가 아니라 정상 경로의 일부다.
   *
   * <p>사전 조회를 먼저 하는 것은 정상 케이스에서 예외를 던지지 않기 위한 최적화일 뿐, 정확성은 제약이 보장한다.
   *
   * <p><b>이 메서드에 {@code @Transactional}이 없는 것은 의도다.</b> JPA는 제약 위반이 나면 트랜잭션을 rollback-only로 표시해 같은
   * 트랜잭션 안에서는 복구 조회조차 못 하게 한다. 그래서 삽입 시도를 {@link PersonInserter}(REQUIRES_NEW)에 가두고, 실패하면 멀쩡한 상태에서
   * 기존 인물을 찾는다.
   */
  public Registration register(NewPerson command) {
    String rrnHash = rrnHasher.hash(command.rrn());

    Person existing = personRepository.findByRrnHash(rrnHash).orElse(null);
    if (existing != null) {
      return new Registration(existing.getId(), false);
    }

    try {
      return new Registration(personInserter.insert(command), true);
    } catch (DataIntegrityViolationException e) {
      // 동시 등록 레이스 — 다른 요청이 방금 같은 인물을 넣었다. 그 인물을 쓴다.
      log.debug("동일 인물 동시 등록 감지 — 기존 인물로 전환한다.");
      Person concurrent =
          personRepository
              .findByRrnHash(rrnHash)
              .orElseThrow(() -> e); // 제약 위반인데 못 찾으면 다른 제약이 깨진 것이다 — 삼키지 않는다
      return new Registration(concurrent.getId(), false);
    }
  }

  /** 주민번호 기반 기존 인물 검사 (설계서 7.2 {@code POST /persons/check-duplicate}). */
  @Transactional(readOnly = true)
  public boolean exists(String rrn) {
    return personRepository.existsByRrnHash(rrnHasher.hash(rrn));
  }

  /** 인물 상세. 개인식별정보는 마스킹된 채로 나간다 (설계서 10.2). */
  @Transactional(readOnly = true)
  public Person get(Long personId) {
    return requirePerson(personId);
  }

  /**
   * 주민번호 복호화 조회 (설계서 7.2 {@code GET /persons/{personId}/rrn}, 10.2).
   *
   * <p><b>접근로그는 이 트랜잭션 안에서 남는다 — 기록 없으면 열람 없음</b>(설계서 10.1.1). 로그 INSERT가 실패하면 이 메서드도 실패해야 한다. 로그인
   * 실패 카운트가 REQUIRES_NEW로 롤백에서 살아남아야 하는 것과 정반대 방향이다.
   *
   * <p>권한({@code person.rrn.decrypt}) 검사는 컨트롤러의 {@code @PreAuthorize}가 한다.
   *
   * @param purpose 열람 사유. 필수 (설계서 10.2)
   */
  @Transactional
  public String decryptRrn(
      Long personId, Long actorUserId, String purpose, String api, String clientIp) {
    if (!StringUtils.hasText(purpose)) {
      throw new BusinessException(PersonErrorCode.PURPOSE_REQUIRED);
    }
    Person person = requirePerson(personId);

    accessLogRepository.save(
        PrivacyAccessLog.of(
            actorUserId, person.getId(), PrivacyAccessType.DECRYPT, api, clientIp, purpose));

    return person.decryptRrn(cipher);
  }

  private Person requirePerson(Long personId) {
    return personRepository
        .findById(personId)
        .orElseThrow(
            () -> new BusinessException(PersonErrorCode.NOT_FOUND, "인물을 찾을 수 없습니다: " + personId));
  }

  /**
   * 등록 결과.
   *
   * @param created 새로 만들었으면 true, 기존 인물을 재사용했으면 false. 호출부(임직원 입사/설계사 후보등록)가 역할만 추가할지 판단한다
   */
  public record Registration(Long personId, boolean created) {}
}
