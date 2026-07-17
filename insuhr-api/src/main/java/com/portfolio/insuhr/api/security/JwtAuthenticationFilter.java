package com.portfolio.insuhr.api.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authorization 헤더의 Bearer 토큰을 SecurityContext에 반영한다 (설계서 7.1).
 *
 * <p>토큰이 없거나 잘못돼도 여기서 401을 던지지 않는다. 인증 없이 통과시키고 인가 단계가 판단하게 둔다 — 공개 엔드포인트(로그인, actuator)가 있기 때문이고,
 * 실제 거부는 Security의 진입점(EntryPoint)이 표준 envelope으로 응답한다.
 *
 * <p>권한은 PERM_CD를 그대로 GrantedAuthority로 싣는다. {@code hasAuthority('agent.write')} 형태로 검사하기 위함이며,
 * {@code hasRole()}이 요구하는 ROLE_ 접두어 규칙을 쓰지 않는다 — 설계서 10.1의 권한 모델은 역할이 아니라 {리소스}.{행위}다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider tokenProvider;

  public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
    this.tokenProvider = tokenProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String token = resolveToken(request);
    if (token != null) {
      try {
        AuthenticatedUser user = tokenProvider.parseAccessToken(token);
        List<SimpleGrantedAuthority> authorities =
            user.permissions().stream().map(SimpleGrantedAuthority::new).toList();

        UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(user, null, authorities);
        authentication.setDetails(user);

        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (JwtException | IllegalArgumentException e) {
        // 만료·위조 토큰. 서버 장애가 아니므로 스택트레이스 없이 남기고 익명으로 진행시킨다.
        log.debug("유효하지 않은 토큰: {}", e.getMessage());
        SecurityContextHolder.clearContext();
      }
    }

    try {
      chain.doFilter(request, response);
    } finally {
      // 스레드 풀 재사용 환경에서 인증이 다음 요청으로 새는 것을 막는다.
      SecurityContextHolder.clearContext();
    }
  }

  private String resolveToken(HttpServletRequest request) {
    String header = request.getHeader(AUTH_HEADER);
    if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
      return header.substring(BEARER_PREFIX.length()).trim();
    }
    return null;
  }
}
