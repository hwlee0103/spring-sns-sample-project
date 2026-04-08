package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.user.User;

public record UserResponse(Long id, String email, String nickname) {
  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
  }
}
