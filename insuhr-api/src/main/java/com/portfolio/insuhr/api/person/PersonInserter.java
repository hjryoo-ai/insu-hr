package com.portfolio.insuhr.api.person;

import com.portfolio.insuhr.common.crypto.AesGcmCipher;
import com.portfolio.insuhr.common.crypto.PepperedHasher;
import com.portfolio.insuhr.domain.person.NewPerson;
import com.portfolio.insuhr.domain.person.Person;
import com.portfolio.insuhr.domain.person.PersonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인물 INSERT를 <b>독립 트랜잭션</b>으로 시도한다 (설계서 5.2 v1.2).
 *
 * <p><b>왜 REQUIRES_NEW인가</b>: 중복의 방어선은 {@code UQ_PERSON_RRN} 제약이고, 위반을 잡아 "기존 인물 재사용"으로 전환하는 것이 정상
 * 경로다. 그런데 JPA에서 제약 위반이 나면 영속성 컨텍스트가 오염되고 트랜잭션이 rollback-only로 표시돼 <b>같은 트랜잭션 안에서는 복구 조회조차 할 수
 * 없다</b>. 삽입 시도를 독립 트랜잭션에 가두면, 실패해도 그 트랜잭션만 롤백되고 호출부는 멀쩡한 상태로 기존 인물을 찾을 수 있다.
 *
 * <p><b>결과적 성질</b>: 인물 행은 호출부 트랜잭션과 무관하게 커밋된다. 따라서 상위 유스케이스(입사, 후보등록)가 뒤에서 실패하면 역할 없는 인물이 남는다. 이는
 * 손상 데이터가 아니라 <b>재사용 가능한 상태</b>다 — 같은 주민번호로 다시 시도하면 그 인물을 찾아 쓴다. 인물 등록이 주민번호 기준으로 멱등이기에 성립하는 절충이다.
 */
@Component
public class PersonInserter {

  private final PersonRepository personRepository;
  private final AesGcmCipher cipher;
  private final PepperedHasher rrnHasher;

  public PersonInserter(
      PersonRepository personRepository, AesGcmCipher cipher, PepperedHasher rrnHasher) {
    this.personRepository = personRepository;
    this.cipher = cipher;
    this.rrnHasher = rrnHasher;
  }

  /**
   * @return 생성된 인물 ID
   * @throws org.springframework.dao.DataIntegrityViolationException 동시 등록으로 유니크 제약 위반. 호출부가 잡아 기존
   *     인물로 전환한다
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Long insert(NewPerson command) {
    // saveAndFlush: 커밋까지 미루면 제약 위반이 이 메서드가 아니라 트랜잭션 종료 시점에 터져
    // 호출부가 잡을 수 없다.
    return personRepository.saveAndFlush(Person.register(command, cipher, rrnHasher)).getId();
  }
}
