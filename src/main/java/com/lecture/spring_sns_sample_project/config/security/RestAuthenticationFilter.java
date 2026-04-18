package com.lecture.spring_sns_sample_project.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.databind.ObjectMapper;

/**
 * REST API 용 인증 필터 — JSON 요청 body 에서 email/password 를 추출하여 {@code
 * UsernamePasswordAuthenticationToken} 을 생성한다.
 *
 * <p>Spring Security 의 {@code formLogin()} DSL 대신 JSON 기반 로그인을 지원하기 위한 커스텀 필터. 인증 성공/실패 처리는 {@link
 * RestAuthSuccessHandler} / {@link RestAuthFailureHandler} 에 위임한다.
 *
 * <p>세션 고정 방어, SecurityContext 저장 등은 부모 클래스가 자동 처리한다.
 */
public class RestAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

  private final ObjectMapper objectMapper;

  public RestAuthenticationFilter(ObjectMapper objectMapper) {
    super(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/login"));
    this.objectMapper = objectMapper;
  }

  @Override
  public Authentication attemptAuthentication(
      HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
    if (!isJsonContentType(request.getContentType())) {
      throw new AuthenticationServiceException(
          "Content-Type 이 application/json 이어야 합니다: " + request.getContentType());
    }

    LoginBody body;
    try {
      body = objectMapper.readValue(request.getInputStream(), LoginBody.class);
    } catch (Exception e) {
      throw new AuthenticationServiceException("요청 본문을 파싱할 수 없습니다.", e);
    }

    String email = body.email() != null ? body.email().trim() : "";
    String password = body.password() != null ? body.password() : "";

    UsernamePasswordAuthenticationToken authRequest =
        UsernamePasswordAuthenticationToken.unauthenticated(email, password);

    return getAuthenticationManager().authenticate(authRequest);
  }

  /** {@code application/json} 및 그 변형({@code application/json; charset=utf-8} 등)을 허용. */
  private static boolean isJsonContentType(String contentType) {
    if (contentType == null) {
      return false;
    }
    try {
      return MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private record LoginBody(String email, String password) {}
}
