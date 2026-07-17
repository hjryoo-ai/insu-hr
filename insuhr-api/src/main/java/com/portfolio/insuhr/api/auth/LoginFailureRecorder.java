package com.portfolio.insuhr.api.auth;

import com.portfolio.insuhr.domain.auth.UserAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 실패 카운트를 <b>독립 트랜잭션</b>으로 기록한다 (설계서 10.1).
 *
 * <p>왜 별도 빈이고 REQUIRES_NEW인가: 로그인 실패는 예외로 끝나고, 그 예외는 호출한 트랜잭션을 롤백시킨다. 같은 트랜잭션에서 카운트를 올리면 롤백과 함께 사라져
 * <b>계정이 영원히 잠기지 않는다</b>. saveAndFlush로도 해결되지 않는다 — flush는 커밋이 아니다.
 *
 * <p>self-invocation은 프록시를 타지 않아 전파 설정이 무시되므로 별도 빈으로 분리했다.
 */
@Component
public class LoginFailureRecorder {

  private final UserAccountRepository userRepository;

  public LoginFailureRecorder(UserAccountRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * @param lockThreshold 정책값 LOGIN_FAIL_LOCK_CNT (설계서 13.1 — 하드코딩 금지)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(Long userId, int lockThreshold) {
    userRepository
        .findById(userId)
        .ifPresent(
            user -> {
              user.recordLoginFailure(lockThreshold);
              userRepository.save(user);
            });
  }
}
