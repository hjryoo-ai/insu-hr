package com.portfolio.insuhr.domain.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * 계정의 살아있는 토큰을 모두 폐기한다 (설계서 10.1 회전 — 재사용 감지 시).
   *
   * <p>엔티티를 하나씩 불러 revoke()하지 않는 이유는 대상이 몇 건인지 모르기 때문이다. 상태 전이가 단순한 일괄 UPDATE이므로 벌크 연산이 맞다.
   *
   * <p>{@code clearAutomatically}: 벌크 UPDATE는 영속성 컨텍스트를 우회하므로, 같은 트랜잭션에 이미 로드된 엔티티가 낡은 상태로 남는다.
   */
  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE RefreshToken t
         SET t.revokedAt = :now
       WHERE t.userId = :userId
         AND t.revokedAt IS NULL
      """)
  int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
