package com.lecture.spring_sns_sample_project.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank(message = "currentPassword는 필수값입니다.")
        @Size(min = 8, max = 64, message = "currentPassword는 8~64자여야 합니다.")
        String currentPassword,
    @NotBlank(message = "newPassword는 필수값입니다.")
        @Size(min = 8, max = 64, message = "newPassword는 8~64자여야 합니다.")
        String newPassword) {}
