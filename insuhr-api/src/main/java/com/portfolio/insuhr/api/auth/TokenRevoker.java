package com.portfolio.insuhr.api.auth;

import com.portfolio.insuhr.domain.auth.RefreshTokenRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정의 모든 refresh 토큰을 <b>독립 트랜잭션</b>으로 무효화한다 (설계서 10.1).
 *
 * <p>{@link LoginFailureRecorder}와 같은 이유로 REQUIRES_NEW다 — 재사용 감지는 401 예외로 끝나고, 그 예외가 호출한 트랜잭션을
 * 롤백시킨다. 같은 트랜잭션에서 무효화하면 롤백과 함께 사라져 <b>탈취된 토큰이 계속 살아있게</b> 된다.
 *
 * <p>"쓰고 나서 예외를 던지는" 경로는 전부 이 함정을 밟는다. 설계서 10.1의 트랜잭션 규약 참조.
 */
@Component
public class TokenRevoker {

  private final RefreshTokenRepository refreshTokenRepository;

  public TokenRevoker(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  /**
   * @return 무효화된 토큰 수
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int revokeAllOf(Long userId) {
    return refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
  }
}
