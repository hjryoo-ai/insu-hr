package com.portfolio.insuhr.api.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 보안 설정 (설계서 10.1).
 *
 * <p>Spring Security 7 기준. 6.x의 체이닝 스타일(`.csrf().disable()`)과 `authorizeRequests()`는 제거됐고
 * Customizer 람다 오버로드만 남았다 — Boot 3 예제를 그대로 옮기면 컴파일되지 않는다(설계서 3.0).
 *
 * <p>{@code @EnableMethodSecurity}로 {@code @PreAuthorize("hasAuthority('agent.write')")}를 켠다. URL
 * 패턴이 아니라 메서드에 권한을 붙이는 이유는, 설계서 7.2의 엔드포인트가 같은 경로에서 메서드별로 다른 권한을 요구하기 때문이다.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

  private final JwtTokenProvider tokenProvider;
  private final SecurityErrorResponder securityErrorResponder;

  public SecurityConfig(
      JwtTokenProvider tokenProvider, SecurityErrorResponder securityErrorResponder) {
    this.tokenProvider = tokenProvider;
    this.securityErrorResponder = securityErrorResponder;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        // 세션이 없고 토큰만 쓰므로 CSRF 공격의 전제(브라우저가 쿠키를 자동 첨부)가 성립하지 않는다.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // 폼로그인·기본인증은 쓰지 않는다. 인증은 /auth/login → JWT 하나로 통일한다.
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(
            auth ->
                auth
                    // 로그인·토큰 재발급은 인증 전에 호출된다.
                    .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh")
                    .permitAll()
                    .requestMatchers("/api/v1/auth/system-token")
                    .permitAll()
                    // 컨테이너 오케스트레이터가 인증 없이 찔러야 한다.
                    .requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    // 나머지 actuator는 운영자만.
                    .requestMatchers("/actuator/**")
                    .hasAuthority("system.monitor")
                    .requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    // 설계서 7.1: 로그인 제외 전 API 인증. 개별 권한은 @PreAuthorize가 본다.
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(securityErrorResponder)
                    .accessDeniedHandler(securityErrorResponder))
        .addFilterBefore(
            new JwtAuthenticationFilter(tokenProvider), UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  /**
   * BCrypt (설계서 10.1).
   *
   * <p>이 빈이 api 모듈에 있는 이유: BCrypt는 spring-security-crypto가 필요해서 insuhr-common(의존성 0)에 둘 수 없다. 반면
   * AES/SHA-256은 JDK 내장 JCA만으로 되므로 common에 있다(설계서 13.2 Phase 1).
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
