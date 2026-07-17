package com.portfolio.insuhr.domain.person;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

  /**
   * 주민번호 해시로 동일인을 찾는다 (설계서 5.2).
   *
   * <p>암호문(RRN_ENC)은 IV 때문에 같은 값이라도 매번 달라 등치 검색이 불가능하다. 그래서 결정적 해시로 찾는다(설계서 6.8).
   */
  Optional<Person> findByRrnHash(String rrnHash);

  boolean existsByRrnHash(String rrnHash);
}
