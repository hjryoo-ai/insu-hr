package com.portfolio.insuhr.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.domain.auth.AuthorityQueryDao;
import com.portfolio.insuhr.domain.auth.UserAccount;
import com.portfolio.insuhr.domain.auth.UserAccountRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

/**
 * Phase 1 완료 기준: 로그인 → 권한별 403 검증 (설계서 13.2).
 *
 * <p>권한 모델이 실제로 막는지를 확인한다. 설정 파일만 보고는 알 수 없는 부분이다.
 */
class AuthIntegrationTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "portfolio-pw-1234";

  @LocalServerPort int port;

  @Autowired UserAccountRepository userRepository;
  @Autowired AuthorityQueryDao authorityQueryDao;
  @Autowired PasswordEncoder passwordEncoder;

  private RestClient client;

  @BeforeEach
  void setUp() {
    client = RestClient.builder().baseUrl("http://localhost:" + port).build();
  }

  /** 지정한 역할을 가진 계정을 만들고 로그인해 access 토큰을 얻는다. */
  private String loginWithRole(String loginId, String roleCd) {
    UserAccount user =
        userRepository.save(UserAccount.ofHuman(loginId, passwordEncoder.encode(PASSWORD), null));
    authorityQueryDao.grantRole(user.getId(), roleCd, "TEST");
    return accessTokenOf(loginId, PASSWORD);
  }

  @SuppressWarnings("unchecked")
  private String accessTokenOf(String loginId, String password) {
    Map<String, Object> body =
        client
            .post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("loginId", loginId, "password", password))
            .retrieve()
            .body(Map.class);

    assertThat(body).containsEntry("success", true);
    return (String) ((Map<String, Object>) body.get("data")).get("accessToken");
  }

  private HttpStatusCode getPolicyStatus(String accessToken) {
    RestClient.RequestHeadersSpec<?> spec = client.get().uri("/api/v1/policies/PWD_MIN_LENGTH");
    if (accessToken != null) {
      spec = spec.header("Authorization", "Bearer " + accessToken);
    }
    return spec.retrieve()
        .onStatus(status -> true, (req, res) -> {})
        .toBodilessEntity()
        .getStatusCode();
  }

  @Test
  @DisplayName("로그인하면 Access/Refresh 토큰이 발급된다")
  @SuppressWarnings("unchecked")
  void loginIssuesTokens() {
    userRepository.save(UserAccount.ofHuman("login-ok", passwordEncoder.encode(PASSWORD), null));

    Map<String, Object> body =
        client
            .post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("loginId", "login-ok", "password", PASSWORD))
            .retrieve()
            .body(Map.class);

    Map<String, Object> data = (Map<String, Object>) body.get("data");
    assertThat(data.get("accessToken")).asString().isNotBlank();
    assertThat(data.get("refreshToken")).asString().isNotBlank();
    assertThat(data).containsEntry("tokenType", "Bearer");
    // 유효시간은 정책값 ACCESS_TOKEN_TTL_MINUTES(30분)에서 온다 — 하드코딩이 아님을 확인
    assertThat(((Number) data.get("expiresIn")).longValue()).isEqualTo(1800L);
  }

  @Test
  @DisplayName("권한이 있으면 200 — IT_ADMIN은 policy.read를 가진다")
  void allowsRequestWithRequiredAuthority() {
    String token = loginWithRole("policy-reader", "IT_ADMIN");
    assertThat(getPolicyStatus(token).value()).isEqualTo(200);
  }

  @Test
  @DisplayName("권한이 없으면 403 — SUPPORT_STAFF는 policy.read가 없다")
  void deniesRequestWithoutRequiredAuthority() {
    // Phase 1 완료 기준의 핵심. 인증은 됐지만 권한이 없는 경우 401이 아니라 403이어야 한다.
    String token = loginWithRole("support-staff", "SUPPORT_STAFF");
    assertThat(getPolicyStatus(token).value()).isEqualTo(403);
  }

  @Test
  @DisplayName("토큰이 없으면 401")
  void deniesRequestWithoutToken() {
    assertThat(getPolicyStatus(null).value()).isEqualTo(401);
  }

  @Test
  @DisplayName("위조된 토큰이면 401")
  void deniesForgedToken() {
    String forged =
        "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicGVybXMiOlsicG9saWN5LnJlYWQiXX0.bogus-signature";
    assertThat(getPolicyStatus(forged).value()).isEqualTo(401);
  }

  @Test
  @DisplayName("인증 거부(401) 응답도 표준 envelope 형식이다")
  void unauthorizedResponseUsesStandardEnvelope() {
    // 필터 체인의 거부는 @RestControllerAdvice가 못 잡으므로 SecurityErrorResponder가 낸다.
    // 클라이언트가 응답 형식을 두 벌 다루지 않도록 envelope이 동일해야 한다.
    String body =
        client
            .get()
            .uri("/api/v1/policies/PWD_MIN_LENGTH")
            .retrieve()
            .onStatus(status -> true, (req, res) -> {})
            .body(String.class);

    assertThat(body)
        .contains("\"success\":false")
        .contains("\"code\":\"COM-4011\"")
        .contains("\"traceId\"");
  }

  @Test
  @DisplayName("인가 거부(403) 응답도 같은 envelope 형식이다")
  void forbiddenResponseUsesStandardEnvelope() {
    // 403은 필터가 아니라 @PreAuthorize에서 나므로 401과 경로가 다르다. 형식이 갈리기 쉬운 지점.
    String token = loginWithRole("envelope-403", "SUPPORT_STAFF");
    String body =
        client
            .get()
            .uri("/api/v1/policies/PWD_MIN_LENGTH")
            .header("Authorization", "Bearer " + token)
            .retrieve()
            .onStatus(status -> true, (req, res) -> {})
            .body(String.class);

    assertThat(body)
        .contains("\"success\":false")
        .contains("\"code\":\"COM-4031\"")
        .contains("\"traceId\"");
  }

  @Test
  @DisplayName("refresh 하면 새 토큰이 나오고 쓰인 토큰은 폐기된다 (회전)")
  @SuppressWarnings("unchecked")
  void refreshRotatesToken() {
    userRepository.save(UserAccount.ofHuman("rotate-me", passwordEncoder.encode(PASSWORD), null));

    Map<String, Object> first =
        (Map<String, Object>)
            client
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("loginId", "rotate-me", "password", PASSWORD))
                .retrieve()
                .body(Map.class)
                .get("data");
    String refreshToken = (String) first.get("refreshToken");

    Map<String, Object> refreshed =
        client
            .post()
            .uri("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("refreshToken", refreshToken))
            .retrieve()
            .body(Map.class);
    assertThat(refreshed).containsEntry("success", true);

    // 같은 refresh 토큰을 다시 쓰면 거부돼야 한다 — 회전의 핵심.
    HttpStatusCode reuse =
        client
            .post()
            .uri("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("refreshToken", refreshToken))
            .retrieve()
            .onStatus(status -> true, (req, res) -> {})
            .toBodilessEntity()
            .getStatusCode();
    assertThat(reuse.value()).isEqualTo(401);
  }

  @Test
  @DisplayName("폐기된 refresh 토큰이 재사용되면 그 계정의 모든 토큰이 무효화된다")
  @SuppressWarnings("unchecked")
  void reusedRefreshTokenRevokesAllTokensOfUser() {
    userRepository.save(
        UserAccount.ofHuman("reuse-detect", passwordEncoder.encode(PASSWORD), null));

    Map<String, Object> first =
        (Map<String, Object>)
            client
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("loginId", "reuse-detect", "password", PASSWORD))
                .retrieve()
                .body(Map.class)
                .get("data");
    String stolenToken = (String) first.get("refreshToken");

    // 정상 회전 — stolenToken은 폐기되고 newToken이 나온다
    Map<String, Object> rotated =
        (Map<String, Object>)
            client
                .post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", stolenToken))
                .retrieve()
                .body(Map.class)
                .get("data");
    String newToken = (String) rotated.get("refreshToken");

    // 공격자가 탈취한 옛 토큰을 사용 → 재사용 감지
    assertThat(refreshStatus(stolenToken).value()).isEqualTo(401);

    // 정상 사용자가 쥐고 있던 새 토큰도 함께 끊긴다. 누가 진짜인지 서버는 알 수 없으므로
    // 둘 다 끊고 재로그인시키는 것이 회전의 취지다.
    assertThat(refreshStatus(newToken).value()).isEqualTo(401);
  }

  private HttpStatusCode refreshStatus(String refreshToken) {
    return client
        .post()
        .uri("/api/v1/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("refreshToken", refreshToken))
        .retrieve()
        .onStatus(s -> true, (req, res) -> {})
        .toBodilessEntity()
        .getStatusCode();
  }

  @Test
  @DisplayName("비밀번호가 틀리면 401이고, 연속 실패가 임계값에 닿으면 계정이 잠긴다")
  void locksAccountAfterConsecutiveFailures() {
    UserAccount user =
        userRepository.save(UserAccount.ofHuman("lock-me", passwordEncoder.encode(PASSWORD), null));

    // 정책값 LOGIN_FAIL_LOCK_CNT = 5
    for (int i = 0; i < 5; i++) {
      HttpStatusCode status =
          client
              .post()
              .uri("/api/v1/auth/login")
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("loginId", "lock-me", "password", "wrong-password"))
              .retrieve()
              .onStatus(s -> true, (req, res) -> {})
              .toBodilessEntity()
              .getStatusCode();
      assertThat(status.value()).isEqualTo(401);
    }

    UserAccount locked = userRepository.findById(user.getId()).orElseThrow();
    assertThat(locked.isLocked()).isTrue();

    // 잠긴 뒤에는 올바른 비밀번호로도 못 들어간다
    HttpStatusCode afterLock =
        client
            .post()
            .uri("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("loginId", "lock-me", "password", PASSWORD))
            .retrieve()
            .onStatus(s -> true, (req, res) -> {})
            .toBodilessEntity()
            .getStatusCode();
    assertThat(afterLock.value()).isEqualTo(401);
  }
}
