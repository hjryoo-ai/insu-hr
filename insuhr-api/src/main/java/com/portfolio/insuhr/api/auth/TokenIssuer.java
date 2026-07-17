package com.portfolio.insuhr.api.auth;

import com.portfolio.insuhr.api.security.JwtTokenProvider;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.auth.AuthorityQueryDao;
import com.portfolio.insuhr.domain.auth.RefreshToken;
import com.portfolio.insuhr.domain.auth.RefreshTokenRepository;
import com.portfolio.insuhr.domain.auth.UserAccount;
import com.portfolio.insuhr.domain.auth.UserAccountRepository;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토큰 발급 (설계서 10.1).
 *
 * <p><b>왜 AuthService가 아니라 별도 빈인가</b>: 발급은 쓰기(Refresh 토큰 INSERT, 계정 갱신)라 트랜잭션이 필요하다. 그렇다고 {@code
 * login()}에 {@code @Transactional}을 걸면, 실패 경로의 {@link LoginFailureRecorder}(REQUIRES_NEW)가 <b>바깥
 * 커넥션을 쥔 채 두 번째 커넥션</b>을 풀에서 꺼내게 된다. 로그인은 동시성이 몰리는 엔드포인트라, 모든 스레드가 첫 커넥션을 잡고 두 번째를 기다리는 자기 고갈(pool
 * starvation)이 가능하다.
 *
 * <p>그래서 인증 검증(읽기)은 트랜잭션 밖에 두고, 쓰기가 필요한 순간에만 트랜잭션을 연다. 전파는 기본값(REQUIRED)이므로 {@code refresh()}처럼 이미
 * 트랜잭션 안에서 부르면 합류해 커넥션이 하나로 유지된다.
 */
@Component
public class TokenIssuer {

  private static final int REFRESH_TOKEN_BYTES = 32;

  private final UserAccountRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthorityQueryDao authorityQueryDao;
  private final PolicyConfigService policyConfigService;
  private final JwtTokenProvider tokenProvider;
  private final SecureRandom random = new SecureRandom();

  public TokenIssuer(
      UserAccountRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      AuthorityQueryDao authorityQueryDao,
      PolicyConfigService policyConfigService,
      JwtTokenProvider tokenProvider) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.authorityQueryDao = authorityQueryDao;
    this.policyConfigService = policyConfigService;
    this.tokenProvider = tokenProvider;
  }

  /** 로그인 성공 → 마지막 로그인 시각 갱신 + 실패 카운트 초기화 + 토큰 발급. */
  @Transactional
  public AuthService.TokenPair issueForLogin(Long userId) {
    UserAccount user = load(userId);
    user.recordLoginSuccess();
    return issue(user);
  }

  /**
   * 토큰 재발급 → 발급만 한다.
   *
   * <p>로그인이 아니므로 LAST_LOGIN_AT을 건드리지 않는다 — refresh로 마지막 로그인 시각이 갱신되면 "언제 실제로 로그인했나"를 알 수 없게 된다.
   */
  @Transactional
  public AuthService.TokenPair issueForRefresh(Long userId) {
    return issue(load(userId));
  }

  private UserAccount load(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));
  }

  private AuthService.TokenPair issue(UserAccount user) {
    Set<String> permissions = authorityQueryDao.findPermissionCodes(user.getId());

    Duration accessTtl =
        Duration.ofMinutes(policyConfigService.getInt(PolicyKey.ACCESS_TOKEN_TTL_MINUTES));
    Duration refreshTtl =
        Duration.ofDays(policyConfigService.getInt(PolicyKey.REFRESH_TOKEN_TTL_DAYS));

    String accessToken =
        tokenProvider.issueAccessToken(user.getId(), user.getLoginId(), permissions, accessTtl);

    // Refresh는 JWT가 아니라 불투명(opaque) 난수다. 서버가 DB로 상태를 관리하므로 자체 서술적일
    // 이유가 없고, 짧을수록 유출 표면이 작다.
    String refreshToken = generateRefreshToken();
    refreshTokenRepository.save(
        RefreshToken.issue(
            user.getId(), TokenHasher.sha256(refreshToken), Instant.now().plus(refreshTtl)));

    return new AuthService.TokenPair(accessToken, refreshToken, accessTtl.toSeconds());
  }

  private String generateRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
