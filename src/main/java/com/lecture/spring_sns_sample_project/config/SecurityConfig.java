package com.lecture.spring_sns_sample_project.config;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment env)
      throws Exception {
    // CSRF: 쿠키 기반 (XSRF-TOKEN), JS 가 읽을 수 있도록 HttpOnly 비활성
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfRequestHandler)
                    // H2 콘솔은 dev 편의를 위해 CSRF 면제
                    .ignoringRequestMatchers("/h2-console/**"))
        // 모든 응답에서 CSRF 쿠키를 강제 materialize
        .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            auth ->
                auth
                    // 공개 엔드포인트
                    .requestMatchers(HttpMethod.POST, "/api/user")
                    .permitAll() // 회원가입
                    .requestMatchers("/api/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/post", "/api/post/*")
                    .permitAll() // 비로그인 피드 열람 허용
                    .requestMatchers(HttpMethod.GET, "/api/user", "/api/user/*")
                    .permitAll() // 사용자 프로필 열람
                    .requestMatchers("/h2-console/**")
                    .permitAll()
                    // 그 외 모두 인증 필요 (/api/auth/me 포함 — 비로그인은 401 반환)
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            ex ->
                // 인증 실패 시 SPA 에 적합한 401 반환 (기본 403/302 대신)
                ex.authenticationEntryPoint(unauthorizedEntryPoint()))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, authentication) -> res.setStatus(204)));

    // H2 콘솔 iframe 허용은 dev 프로필에서만. 운영 환경은 deny 로 clickjacking 방어.
    boolean devProfile = Arrays.asList(env.getActiveProfiles()).contains("dev");
    http.headers(
        headers ->
            headers.frameOptions(
                frame -> {
                  if (devProfile) {
                    frame.sameOrigin();
                  } else {
                    frame.deny();
                  }
                }));

    return http.build();
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  /** 인증 실패 시 401 만 반환 (Body 비움) — SPA 가 fetchCurrentUser null fallback 으로 처리. */
  private static AuthenticationEntryPoint unauthorizedEntryPoint() {
    return (request, response, authException) ->
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  /**
   * 로그인 성공 시 세션 ID 를 새로 발급하여 세션 고정 공격(Session Fixation)을 방어한다. {@code AuthController} 가 수동
   * authenticate 경로를 사용하므로 이 빈을 직접 호출해야 한다.
   */
  @Bean
  public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
    return new ChangeSessionIdAuthenticationStrategy();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }
}
