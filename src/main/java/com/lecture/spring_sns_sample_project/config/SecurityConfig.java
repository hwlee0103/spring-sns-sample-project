package com.lecture.spring_sns_sample_project.config;

import com.lecture.spring_sns_sample_project.config.security.RestAuthFailureHandler;
import com.lecture.spring_sns_sample_project.config.security.RestAuthSuccessHandler;
import com.lecture.spring_sns_sample_project.config.security.RestAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ConcurrentSessionControlAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.session.ConcurrentSessionFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final SessionRegistry sessionRegistry;
  private final AppSessionProperties sessionProperties;

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      Environment env,
      AuthenticationManager authenticationManager,
      ObjectMapper objectMapper,
      RateLimitProperties rateLimitProperties)
      throws Exception {
    boolean devProfile = Arrays.asList(env.getActiveProfiles()).contains("dev");

    // CSRF: 쿠키 기반 (XSRF-TOKEN), JS 가 읽을 수 있도록 HttpOnly 비활성
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    // XorCsrfTokenRequestAttributeHandler: 토큰을 XOR 마스킹하여 BREACH 압축 공격 완화
    XorCsrfTokenRequestAttributeHandler csrfRequestHandler =
        new XorCsrfTokenRequestAttributeHandler();

    // JSON 로그인 필터 — 기존 formLogin 대신 REST API 용 커스텀 필터 사용
    RestAuthenticationFilter loginFilter =
        restAuthenticationFilter(authenticationManager, objectMapper);

    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfTokenRepository)
                    .csrfTokenRequestHandler(csrfRequestHandler)
                    .ignoringRequestMatchers("/h2-console/**"))
        .addFilterAfter(csrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            rateLimitFilter(rateLimitProperties), UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(absoluteSessionTimeoutFilter(), ConcurrentSessionFilter.class)
        // formLogin 대신 커스텀 JSON 인증 필터 등록
        .addFilterAt(loginFilter, UsernamePasswordAuthenticationFilter.class)
        .sessionManagement(
            session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    // sessionFixation 은 표준 필터 전용. 커스텀 RestAuthenticationFilter 는
                    // CompositeSessionAuthenticationStrategy 에서 별도 처리한다.
                    .sessionFixation(fix -> fix.changeSessionId())
                    .maximumSessions(sessionProperties.maxSessionsPerUser())
                    .sessionRegistry(sessionRegistry)
                    .expiredSessionStrategy(
                        event -> {
                          var response = event.getResponse();
                          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                          response.setContentType("application/json;charset=UTF-8");
                          response.getWriter().write("{\"message\":\"세션이 만료되었습니다.\"}");
                        }))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/user")
                    .permitAll()
                    .requestMatchers("/api/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/post", "/api/post/*")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/user", "/api/user/*", "/api/user/by-nickname/*")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/h2-console/**")
                    .access(
                        (authentication, context) ->
                            new org.springframework.security.authorization.AuthorizationDecision(
                                devProfile))
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler((req, res, authentication) -> res.setStatus(204)));

    http.headers(
        headers -> {
          headers.frameOptions(
              frame -> {
                if (devProfile) frame.sameOrigin();
                else frame.deny();
              });
          headers.contentSecurityPolicy(
              csp ->
                  csp.policyDirectives(
                      "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:"));
        });

    if (!devProfile) {
      http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
    }

    return http.build();
  }

  /**
   * REST JSON 인증 필터.
   *
   * <p>Spring Security 의 {@code AbstractAuthenticationProcessingFilter} 를 확장하여:
   *
   * <ul>
   *   <li>세션 고정 방어 ({@link ChangeSessionIdAuthenticationStrategy}) 를 자동 호출
   *   <li>SecurityContext 를 HttpSession 에 자동 저장
   *   <li>성공 시 {@link RestAuthSuccessHandler} → UserResponse JSON 반환
   *   <li>실패 시 {@link RestAuthFailureHandler} → 401 JSON 반환
   * </ul>
   */
  @Bean
  public RestAuthenticationFilter restAuthenticationFilter(
      AuthenticationManager authenticationManager, ObjectMapper objectMapper) {
    RestAuthenticationFilter filter = new RestAuthenticationFilter(objectMapper);
    filter.setAuthenticationManager(authenticationManager);
    filter.setAuthenticationSuccessHandler(new RestAuthSuccessHandler(objectMapper));
    filter.setAuthenticationFailureHandler(new RestAuthFailureHandler(objectMapper));
    filter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy());
    filter.setSecurityContextRepository(securityContextRepository());
    return filter;
  }

  @Bean
  public CsrfCookieFilter csrfCookieFilter() {
    return new CsrfCookieFilter();
  }

  @Bean
  public RateLimitFilter rateLimitFilter(RateLimitProperties rateLimitProperties) {
    return new RateLimitFilter(rateLimitProperties);
  }

  @Bean
  public AbsoluteSessionTimeoutFilter absoluteSessionTimeoutFilter() {
    return new AbsoluteSessionTimeoutFilter(sessionProperties.absoluteTimeout());
  }

  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  private static AuthenticationEntryPoint unauthorizedEntryPoint() {
    return (request, response, authException) ->
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  /**
   * 커스텀 REST 인증 필터용 세션 전략.
   *
   * <p>Spring Security DSL 의 {@code sessionManagement} 는 {@code
   * UsernamePasswordAuthenticationFilter} 등 표준 필터에만 자동 적용된다. 커스텀 {@link RestAuthenticationFilter}
   * 에는 이 복합 전략을 수동 설정해야 동시 세션 제어가 로그인 시점에 정상 작동한다.
   *
   * <ol>
   *   <li>{@link ConcurrentSessionControlAuthenticationStrategy} — 동시 세션 수(3) 초과 검사
   *   <li>{@link ChangeSessionIdAuthenticationStrategy} — 세션 고정 공격 방어
   *   <li>{@link RegisterSessionAuthenticationStrategy} — 새 세션을 레지스트리에 등록
   * </ol>
   */
  @Bean
  public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
    ConcurrentSessionControlAuthenticationStrategy concurrentStrategy =
        new ConcurrentSessionControlAuthenticationStrategy(sessionRegistry);
    concurrentStrategy.setMaximumSessions(sessionProperties.maxSessionsPerUser());

    ChangeSessionIdAuthenticationStrategy fixationStrategy =
        new ChangeSessionIdAuthenticationStrategy();

    RegisterSessionAuthenticationStrategy registerStrategy =
        new RegisterSessionAuthenticationStrategy(sessionRegistry);

    return new CompositeSessionAuthenticationStrategy(
        List.of(concurrentStrategy, fixationStrategy, registerStrategy));
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }
}
