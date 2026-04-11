package com.lecture.spring_sns_sample_project.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security 6 의 CSRF 토큰은 lazy 생성이므로, 응답이 토큰을 참조하지 않으면 쿠키가 내려가지 않는다. 이 필터는 모든 요청에서 토큰을
 * `getToken()` 으로 강제 materialize 하여 SPA 클라이언트가 항상 `XSRF-TOKEN` 쿠키를 받을 수 있도록 보장한다.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    if (csrfToken != null) {
      csrfToken.getToken();
    }
    filterChain.doFilter(request, response);
  }
}
