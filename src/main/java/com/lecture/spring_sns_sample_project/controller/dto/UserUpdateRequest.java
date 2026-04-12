package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.user.UserUpdateCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @NotBlank(message = "nickname은 필수값입니다.")
        @Size(min = 2, max = 20, message = "nickname은 2~20자여야 합니다.")
        String nickname,
    @NotBlank(message = "password는 필수값입니다.")
        @Size(min = 8, max = 64, message = "password는 8~64자여야 합니다.")
        String password) {

  /** Controller-Service 결합도 완화 — Service 는 HTTP DTO 가 아닌 Command 로 입력을 받는다. */
  public UserUpdateCommand toCommand() {
    return new UserUpdateCommand(nickname, password);
  }
}
