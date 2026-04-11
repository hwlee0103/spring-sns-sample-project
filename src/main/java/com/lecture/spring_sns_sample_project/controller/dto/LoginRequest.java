package com.lecture.spring_sns_sample_project.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "email은 필수값입니다.") @Email(message = "email 형식이 올바르지 않습니다.") String email,
    @NotBlank(message = "password는 필수값입니다.") String password) {}
