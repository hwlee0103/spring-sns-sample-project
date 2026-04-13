package com.lecture.spring_sns_sample_project.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증 실패 시 401 + JSON 에러 메시지를 반환한다.
 *
 * <p>{@code BadCredentialsException}, {@code LockedException}, {@code DisabledException} 등 모든
 * {@link AuthenticationException} 하위를 통일적으로 처리한다.
 */
public class RestAuthFailureHandler implements AuthenticationFailureHandler {

  private final ObjectMapper objectMapper;

  public RestAuthFailureHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    Map<String, String> body = Map.of("message", "이메일 또는 비밀번호가 올바르지 않습니다.");
    objectMapper.writeValue(response.getWriter(), body);
  }
}
