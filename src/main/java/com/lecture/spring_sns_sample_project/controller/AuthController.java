package com.lecture.spring_sns_sample_project.controller;

import com.lecture.spring_sns_sample_project.controller.dto.LoginRequest;
import com.lecture.spring_sns_sample_project.controller.dto.UserResponse;
import com.lecture.spring_sns_sample_project.domain.user.AuthUser;
import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
  private final UserService userService;

  @PostMapping("/api/auth/login")
  public ResponseEntity<UserResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    try {
      Authentication authentication =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(request.email(), request.password()));

      // (1) 세션 고정 공격 방어 — 로그인 성공 시 세션 ID 를 새로 발급
      sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

      // (2) SecurityContext 저장
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(authentication);
      SecurityContextHolder.setContext(context);
      securityContextRepository.saveContext(context, httpRequest, httpResponse);

      User user = userService.getByEmail(request.email());
      return ResponseEntity.ok(UserResponse.from(user));
    } catch (BadCredentialsException e) {
      return ResponseEntity.status(401).build();
    }
  }

  @GetMapping("/api/auth/me")
  public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthUser authUser) {
    if (authUser == null) {
      return ResponseEntity.status(401).build();
    }
    User user = userService.getById(authUser.getId());
    return ResponseEntity.ok(UserResponse.from(user));
  }
}
