package com.lecture.spring_sns_sample_project.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @NotBlank(message = "nickname은 필수값입니다.")
        @Size(min = 2, max = 20, message = "nickname은 2~20자여야 합니다.")
        String nickname,
    @NotBlank(message = "password는 필수값입니다.")
        @Size(min = 8, max = 64, message = "password는 8~64자여야 합니다.")
        String password) {}
