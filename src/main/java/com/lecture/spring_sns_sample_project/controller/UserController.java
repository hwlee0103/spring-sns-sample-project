package com.lecture.spring_sns_sample_project.controller;

import com.lecture.spring_sns_sample_project.controller.dto.ChangePasswordRequest;
import com.lecture.spring_sns_sample_project.controller.dto.PageResponse;
import com.lecture.spring_sns_sample_project.controller.dto.UserCreateRequest;
import com.lecture.spring_sns_sample_project.controller.dto.UserResponse;
import com.lecture.spring_sns_sample_project.controller.dto.UserSummaryResponse;
import com.lecture.spring_sns_sample_project.controller.dto.UserUpdateRequest;
import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserService;
import com.lecture.spring_sns_sample_project.domain.user.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final SecurityContextRepository securityContextRepository;
  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  @PostMapping("/api/user")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody UserCreateRequest request) {
    User user = userService.register(request.email(), request.password(), request.nickname());
    UserResponse response = UserResponse.from(user);
    return ResponseEntity.created(URI.create("/api/user/" + user.getId())).body(response);
  }

  @GetMapping("/api/user/{id}")
  public ResponseEntity<UserSummaryResponse> getUser(@PathVariable Long id) {
    User user = userService.getById(id);
    return ResponseEntity.ok(UserSummaryResponse.from(user));
  }

  @GetMapping("/api/user/by-nickname/{nickname}")
  public ResponseEntity<UserSummaryResponse> getUserByNickname(@PathVariable String nickname) {
    User user = userService.getByNickname(nickname);
    return ResponseEntity.ok(UserSummaryResponse.from(user));
  }

  @GetMapping("/api/user")
  public ResponseEntity<PageResponse<UserSummaryResponse>> getUsers(
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    PageResponse<UserSummaryResponse> response =
        PageResponse.from(userService.getAll(pageable), UserSummaryResponse::from);
    return ResponseEntity.ok(response);
  }

  @PutMapping("/api/user/{id}")
  public ResponseEntity<UserResponse> updateUser(
      @PathVariable Long id,
      @Valid @RequestBody UserUpdateRequest request,
      @AuthenticationPrincipal AuthUser authUser) {
    requireOwnership(authUser, id);
    User user = userService.update(id, request.nickname());
    return ResponseEntity.ok(UserResponse.from(user));
  }

  @PutMapping("/api/user/{id}/password")
  public ResponseEntity<Void> changePassword(
      @PathVariable Long id,
      @Valid @RequestBody ChangePasswordRequest request,
      @AuthenticationPrincipal AuthUser authUser,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    requireOwnership(authUser, id);
    User updatedUser =
        userService.changePassword(id, request.currentPassword(), request.newPassword());

    // 현재 세션의 AuthUser 를 새 tokenVersion 으로 갱신 — 본인 세션이 즉시 무효화되지 않도록
    refreshSecurityContext(updatedUser, httpRequest, httpResponse);

    // Redis 에서 해당 사용자의 다른 디바이스 세션을 즉시 삭제
    invalidateOtherSessions(authUser.getEmail(), httpRequest.getSession().getId());

    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/user/{id}")
  public ResponseEntity<Void> deleteUser(
      @PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
    requireOwnership(authUser, id);
    userService.delete(id);
    return ResponseEntity.noContent().build();
  }

  /** 본인 리소스인지 검증 — IDOR(Insecure Direct Object Reference) 방어. */
  private static void requireOwnership(AuthUser authUser, Long targetId) {
    if (authUser == null || !authUser.getId().equals(targetId)) {
      throw new org.springframework.security.access.AccessDeniedException(
          "본인의 리소스만 수정/삭제할 수 있습니다.");
    }
  }

  /** 비밀번호 변경 시 현재 세션을 제외한 해당 사용자의 모든 세션을 Redis 에서 즉시 삭제. */
  private void invalidateOtherSessions(String email, String currentSessionId) {
    sessionRepository
        .findByPrincipalName(email)
        .forEach(
            (sessionId, session) -> {
              if (!sessionId.equals(currentSessionId)) {
                sessionRepository.deleteById(sessionId);
              }
            });
  }

  /** 비밀번호 변경 후 현재 세션의 AuthUser 를 새 tokenVersion 으로 갱신. */
  private void refreshSecurityContext(
      User updatedUser, HttpServletRequest request, HttpServletResponse response) {
    AuthUser newAuthUser = AuthUser.from(updatedUser);
    UsernamePasswordAuthenticationToken newAuth =
        new UsernamePasswordAuthenticationToken(
            newAuthUser, newAuthUser.getPassword(), newAuthUser.getAuthorities());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(newAuth);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
  }
}
