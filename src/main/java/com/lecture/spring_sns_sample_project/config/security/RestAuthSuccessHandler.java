package com.lecture.spring_sns_sample_project.config.security;

import com.lecture.spring_sns_sample_project.domain.user.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * 인증 성공 시 {@code AuthUser} 정보를 JSON 으로 응답한다.
 *
 * <p>DB 재조회 없이 {@link AuthUser} 의 id/email/nickname 을 사용하여 UserResponse 와 동일한 형태를 반환한다.
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

    // UserResponse 와 동일한 JSON 구조
    Map<String, Object> body =
        Map.of(
            "id",
            authUser.getId(),
            "email",
            authUser.getEmail(),
            "nickname",
            authUser.getNickname(),
            "role",
            authUser.getRole().name());
    objectMapper.writeValue(response.getWriter(), body);
  }
}
