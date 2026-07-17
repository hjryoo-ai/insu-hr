package com.portfolio.insuhr.api.auth;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.auth.RefreshToken;
import com.portfolio.insuhr.domain.auth.RefreshTokenRepository;
import com.portfolio.insuhr.domain.auth.UserAccount;
import com.portfolio.insuhr.domain.auth.UserAccountRepository;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인·토큰 재발급 유스케이스 (설계서 7.2, 10.1).
 *
 * <p>토큰 수명(Access 30분/Refresh 14일)은 정책값에서 읽는다 — 하드코딩 금지(설계서 13.1).
 *
 * <p><b>트랜잭션 경계에 주의</b>: {@code login()}에는 {@code @Transactional}이 없다. 인증 검증은 읽기뿐이고, 쓰기가 필요한 두
 * 경로(실패 기록 / 토큰 발급)가 각자 트랜잭션을 연다. 바깥 트랜잭션을 열면 실패 경로의 REQUIRES_NEW가 커넥션을 두 개 잡아, 동시성이 몰리는 로그인에서 풀 자기
 * 고갈을 일으킬 수 있다.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserAccountRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PolicyConfigService policyConfigService;
  private final PasswordEncoder passwordEncoder;
  private final LoginFailureRecorder loginFailureRecorder;
  private final TokenIssuer tokenIssuer;
  private final TokenRevoker tokenRevoker;

  public AuthService(
      UserAccountRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PolicyConfigService policyConfigService,
      PasswordEncoder passwordEncoder,
      LoginFailureRecorder loginFailureRecorder,
      TokenIssuer tokenIssuer,
      TokenRevoker tokenRevoker) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.policyConfigService = policyConfigService;
    this.passwordEncoder = passwordEncoder;
    this.loginFailureRecorder = loginFailureRecorder;
    this.tokenIssuer = tokenIssuer;
    this.tokenRevoker = tokenRevoker;
  }

  /**
   * 로그인 → Access/Refresh 발급 (설계서 7.2).
   *
   * <p>트랜잭션이 없는 것은 의도다 — 클래스 주석 참조.
   */
  public TokenPair login(String loginId, String rawPassword) {
    UserAccount user =
        userRepository
            .findByLoginId(loginId)
            .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

    // 기계 계정은 비밀번호 로그인을 하지 않는다 (/auth/system-token 경로를 쓴다).
    if (user.isSystemAccount()) {
      throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }
    if (user.isLocked()) {
      throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
    }
    if (!user.isLoginAllowed()) {
      throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
    }

    if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      // 독립 트랜잭션으로 기록한다. 아래 예외가 트랜잭션을 롤백시키므로 같은 트랜잭션에서
      // 올린 카운트는 사라진다 — 그러면 계정이 영원히 안 잠긴다(LoginFailureRecorder 주석).
      // 실패 카운트 증가와 잠금 판정 자체는 엔티티가 한다(설계서 4.3 — 규칙은 도메인에).
      loginFailureRecorder.record(
          user.getId(), policyConfigService.getInt(PolicyKey.LOGIN_FAIL_LOCK_CNT));
      throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    return tokenIssuer.issueForLogin(user.getId());
  }

  /**
   * Refresh 토큰으로 재발급 — 회전 (설계서 10.1).
   *
   * <p>쓰인 토큰은 즉시 폐기하고 새로 발급한다. <b>이미 폐기된 토큰이 다시 오면 해당 계정의 모든 토큰을 무효화한다</b> — 정상 클라이언트는 폐기된 토큰을 다시 쓸
   * 이유가 없으므로, 재사용은 토큰이 탈취돼 공격자와 정상 사용자가 같은 토큰을 나눠 쓰는 상황을 시사한다. 누가 진짜인지 서버는 알 수 없으니 둘 다 끊고 재로그인을
   * 시킨다.
   */
  @Transactional
  public TokenPair refresh(String refreshToken) {
    RefreshToken stored =
        refreshTokenRepository
            .findByTokenHash(TokenHasher.sha256(refreshToken))
            .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

    if (stored.getRevokedAt() != null) {
      log.warn("폐기된 refresh 토큰이 재사용됐다. 탈취 가능성 — 계정의 모든 토큰을 무효화한다. userId={}", stored.getUserId());
      // 독립 트랜잭션으로 무효화한다. 아래 예외가 이 트랜잭션을 롤백시키므로 여기서 UPDATE 하면
      // 무효화가 되돌아가 탈취된 토큰이 계속 살아있게 된다 (TokenRevoker 주석).
      tokenRevoker.revokeAllOf(stored.getUserId());
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    if (!stored.isUsableAt(Instant.now())) {
      throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    UserAccount user =
        userRepository
            .findById(stored.getUserId())
            .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
    if (!user.isLoginAllowed()) {
      throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
    }

    stored.revoke();
    // 이미 트랜잭션 안이므로 TokenIssuer(REQUIRED)가 합류한다 — 커넥션은 하나로 유지된다.
    return tokenIssuer.issueForRefresh(user.getId());
  }

  /**
   * 발급된 토큰 쌍.
   *
   * @param accessToken JWT
   * @param refreshToken 불투명 난수 토큰
   * @param expiresInSeconds accessToken 유효시간(초)
   */
  public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}
}
