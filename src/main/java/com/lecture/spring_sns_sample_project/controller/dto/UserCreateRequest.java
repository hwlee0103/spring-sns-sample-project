package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.user.User;

public record UserCreateRequest(String email, String password, String nickname) {
  public User toEntity() {
    return new User(email, password, nickname);
  }
}
