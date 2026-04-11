package com.lecture.spring_sns_sample_project.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 DTO.
 *
 * <p>비밀번호 인코딩이 Service 책임이므로 toEntity() 메서드는 제공하지 않는다. Controller 가 record 필드를 그대로 Service 에 전달한다.
 */
public record UserCreateRequest(
    @NotBlank(message = "email은 필수값입니다.") @Email(message = "email 형식이 올바르지 않습니다.") String email,
    @NotBlank(message = "password는 필수값입니다.")
        @Size(min = 8, max = 64, message = "password는 8~64자여야 합니다.")
        String password,
    @NotBlank(message = "nickname은 필수값입니다.")
        @Size(min = 2, max = 20, message = "nickname은 2~20자여야 합니다.")
        String nickname) {}
