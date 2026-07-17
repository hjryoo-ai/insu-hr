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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인·토큰 재발급 유스케이스 (설계서 7.2, 10.1).
 *
 * <p>토큰 수명(Access 30분/Refresh 14일)은 정책값에서 읽는다 — 하드코딩 금지(설계서 13.1).
 */
@Service
public class AuthService {

  private static final int REFRESH_TOKEN_BYTES = 32;

  private final UserAccountRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthorityQueryDao authorityQueryDao;
  private final PolicyConfigService policyConfigService;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider tokenProvider;
  private final LoginFailureRecorder loginFailureRecorder;
  private final SecureRandom random = new SecureRandom();

  public AuthService(
      UserAccountRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      AuthorityQueryDao authorityQueryDao,
      PolicyConfigService policyConfigService,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider tokenProvider,
      LoginFailureRecorder loginFailureRecorder) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.authorityQueryDao = authorityQueryDao;
    this.policyConfigService = policyConfigService;
    this.passwordEncoder = passwordEncoder;
    this.tokenProvider = tokenProvider;
    this.loginFailureRecorder = loginFailureRecorder;
  }

  /** 로그인 → Access/Refresh 발급 (설계서 7.2). */
  @Transactional
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
      // 별도 트랜잭션으로 기록한다. 아래 예외가 이 트랜잭션을 롤백시키므로 같은 트랜잭션에
      // 올린 카운트는 사라진다 — 그러면 계정이 영원히 안 잠긴다. LoginFailureRecorder 주석 참조.
      // (실패 카운트 증가와 잠금 판정 자체는 엔티티가 한다 — 설계서 4.3, 규칙은 도메인에.)
      loginFailureRecorder.record(
          user.getId(), policyConfigService.getInt(PolicyKey.LOGIN_FAIL_LOCK_CNT));
      throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }

    user.recordLoginSuccess();
    return issueTokens(user);
  }

  /**
   * Refresh 토큰으로 재발급 (설계서 10.1의 "회전").
   *
   * <p>쓰인 refresh 토큰은 즉시 폐기하고 새로 발급한다. 폐기된 토큰이 다시 오면 유효하지 않다.
   */
  @Transactional
  public TokenPair refresh(String refreshToken) {
    String hash = sha256(refreshToken);
    RefreshToken stored =
        refreshTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

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
    return issueTokens(user);
  }

  private TokenPair issueTokens(UserAccount user) {
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
        RefreshToken.issue(user.getId(), sha256(refreshToken), Instant.now().plus(refreshTtl)));

    return new TokenPair(accessToken, refreshToken, accessTtl.toSeconds());
  }

  private String generateRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Refresh 토큰의 저장용 해시.
   *
   * <p>pepper 없는 순수 SHA-256으로 충분하다 — 토큰은 256비트 난수라 주민번호처럼 전수 대입이 불가능하다(설계서 6.8의 RRN_HASH와 다른 이유).
   */
  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
    }
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
