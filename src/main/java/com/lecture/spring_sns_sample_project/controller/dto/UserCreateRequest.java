package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
    @NotBlank(message = "email은 필수값입니다.") @Email(message = "email 형식이 올바르지 않습니다.") String email,
    @NotBlank(message = "password는 필수값입니다.")
        @Size(min = 8, max = 64, message = "password는 8~64자여야 합니다.")
        String password,
    @NotBlank(message = "nickname은 필수값입니다.")
        @Size(min = 2, max = 20, message = "nickname은 2~20자여야 합니다.")
        String nickname) {
  public User toEntity() {
    return new User(email, password, nickname);
  }
}
