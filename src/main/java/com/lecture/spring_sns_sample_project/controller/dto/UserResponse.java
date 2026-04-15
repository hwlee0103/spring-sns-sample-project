package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.user.Role;
import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.security.AuthUser;

public record UserResponse(Long id, String email, String nickname, Role role) {
  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
  }

  /** 인증 성공 시 세션의 AuthUser 로부터 응답 생성 — DB 재조회 없이 사용. */
  public static UserResponse from(AuthUser authUser) {
    return new UserResponse(
        authUser.getId(), authUser.getEmail(), authUser.getNickname(), authUser.getRole());
  }
}
