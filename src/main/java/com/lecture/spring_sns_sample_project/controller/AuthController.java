package com.lecture.spring_sns_sample_project.controller;

import com.lecture.spring_sns_sample_project.config.ErrorType;
import com.lecture.spring_sns_sample_project.controller.dto.UserResponse;
import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserException;
import com.lecture.spring_sns_sample_project.domain.user.UserService;
import com.lecture.spring_sns_sample_project.domain.user.security.AuthUser;
import com.lecture.spring_sns_sample_project.domain.user.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 관련 컨트롤러.
 *
 * <p>로그인은 {@code RestAuthenticationFilter} + {@code RestAuthSuccessHandler} / {@code
 * RestAuthFailureHandler} 가 처리한다. 이 컨트롤러는 로그인 후 세션 상태 조회 엔드포인트만 제공한다.
 *
 * <p>로그아웃은 Spring Security {@code logout()} DSL 이 {@code POST /api/v1/auth/logout} 을 처리한다.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;

  @GetMapping("/api/v1/auth/me")
  public ResponseEntity<UserResponse> me(
      @CurrentUser AuthUser authUser, HttpServletRequest httpRequest) {
    if (authUser == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    try {
      User user = userService.getById(authUser.getId());

      // tokenVersion 비교 — 비밀번호 변경 등으로 version 이 올라갔으면 이 세션을 무효화
      if (authUser.getTokenVersion() != user.getTokenVersion()) {
        invalidateSession(httpRequest);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }

      return ResponseEntity.ok(UserResponse.from(user));
    } catch (UserException e) {
      if (e.getErrorType() != ErrorType.NOT_FOUND) {
        throw e; // NOT_FOUND 이외의 UserException 은 GlobalExceptionHandler 에 위임
      }
      // 세션은 살아있지만 사용자가 DB 에서 삭제된 경우
      invalidateSession(httpRequest);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  private static void invalidateSession(HttpServletRequest httpRequest) {
    HttpSession session = httpRequest.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
  }
}
