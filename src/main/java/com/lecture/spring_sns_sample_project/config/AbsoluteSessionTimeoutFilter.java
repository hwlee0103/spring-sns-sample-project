package com.lecture.spring_sns_sample_project.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 절대 세션 만료 필터.
 *
 * <p>Sliding Session(idle timeout) 만으로는 탈취된 세션이 활동을 통해 무한 갱신될 수 있다. 이 필터는 세션 생성 시점으로부터의 절대
 * 시간(24시간)을 검증하여, 초과 시 세션을 무효화한다.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

  static final String SESSION_CREATED_AT = "SESSION_CREATED_AT";
  private static final Duration ABSOLUTE_TIMEOUT = Duration.ofHours(24);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    HttpSession session = request.getSession(false);
    if (session != null) {
      Instant createdAt = (Instant) session.getAttribute(SESSION_CREATED_AT);

      if (createdAt == null) {
        // 기존 세션(배포 전 생성)에는 attribute 가 없으므로 실제 생성 시각을 사용
        createdAt = Instant.ofEpochMilli(session.getCreationTime());
        session.setAttribute(SESSION_CREATED_AT, createdAt);
      } else if (Instant.now().isAfter(createdAt.plus(ABSOLUTE_TIMEOUT))) {
        session.invalidate();
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"세션이 만료되었습니다. 다시 로그인해주세요.\"}");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
