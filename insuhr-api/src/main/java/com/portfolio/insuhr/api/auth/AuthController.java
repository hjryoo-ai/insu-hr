package com.portfolio.insuhr.api.auth;

import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API (설계서 7.2 AUT). */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  /** 로그인 → Access(정책값, 기본 30분)/Refresh(정책값, 기본 14일) 발급. */
  @PostMapping("/login")
  public ApiResponse<TokenResponse> login(
      @jakarta.validation.Valid @RequestBody LoginRequest request) {
    AuthService.TokenPair tokens = authService.login(request.loginId(), request.password());
    return ApiResponse.ok(TokenResponse.from(tokens), TraceIdProvider.current());
  }

  /** 토큰 재발급. 쓰인 refresh 토큰은 폐기되고 새 쌍이 나온다(회전). */
  @PostMapping("/refresh")
  public ApiResponse<TokenResponse> refresh(
      @jakarta.validation.Valid @RequestBody RefreshRequest request) {
    AuthService.TokenPair tokens = authService.refresh(request.refreshToken());
    return ApiResponse.ok(TokenResponse.from(tokens), TraceIdProvider.current());
  }

  public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  /**
   * @param tokenType OAuth 2.0 Bearer 관례를 따른다 — 클라이언트가 Authorization 헤더를 조립할 때 참고한다
   */
  public record TokenResponse(
      String accessToken, String refreshToken, String tokenType, long expiresIn) {

    static TokenResponse from(AuthService.TokenPair pair) {
      return new TokenResponse(
          pair.accessToken(), pair.refreshToken(), "Bearer", pair.expiresInSeconds());
    }
  }
}
