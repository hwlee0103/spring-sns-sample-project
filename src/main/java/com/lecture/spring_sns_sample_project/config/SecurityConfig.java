package com.lecture.spring_sns_sample_project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
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
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                    .requestMatchers("/api/auth/login", "/api/auth/me")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/post", "/api/post/*")
                    .permitAll() // 비로그인 피드 열람 허용
                    .requestMatchers(HttpMethod.GET, "/api/user", "/api/user/*")
                    .permitAll() // 사용자 프로필 열람
                    .requestMatchers("/h2-console/**")
                    .permitAll()
                    // 그 외 모두 인증 필요
                    .anyRequest()
                    .authenticated())
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, authentication) -> res.setStatus(204)));

    // H2 콘솔 iframe 허용 (dev only — 운영 배포 시 profile 분리 필요)
    http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

    return http.build();
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
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
