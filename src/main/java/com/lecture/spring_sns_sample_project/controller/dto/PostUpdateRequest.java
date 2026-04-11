package com.lecture.spring_sns_sample_project.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostUpdateRequest(
    @NotBlank(message = "content는 필수값입니다.") @Size(max = 500, message = "content는 500자를 초과할 수 없습니다.")
        String content) {}
