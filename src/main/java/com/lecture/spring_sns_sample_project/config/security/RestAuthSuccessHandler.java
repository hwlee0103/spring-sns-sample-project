package com.lecture.spring_sns_sample_project.config.security;

import com.lecture.spring_sns_sample_project.controller.dto.UserResponse;
import com.lecture.spring_sns_sample_project.domain.user.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증 성공 시 {@code AuthUser} 정보를 JSON 으로 응답한다.
 *
 * <p>DB 재조회 없이 {@link AuthUser} 로부터 {@link UserResponse} 를 생성하여 반환한다.
 */
public class RestAuthSuccessHandler implements AuthenticationSuccessHandler {

  private final ObjectMapper objectMapper;

  public RestAuthSuccessHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    AuthUser authUser = (AuthUser) authentication.getPrincipal();

    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    objectMapper.writeValue(response.getWriter(), UserResponse.from(authUser));
  }
}
