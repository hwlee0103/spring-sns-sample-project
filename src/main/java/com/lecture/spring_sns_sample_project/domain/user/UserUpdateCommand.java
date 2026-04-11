package com.lecture.spring_sns_sample_project.domain.user;

/**
 * 사용자 정보 수정 입력값 — Service 계층 입력 모델.
 *
 * <p>Controller(HTTP) DTO 인 {@code UserUpdateRequest} 와 분리하여 Service 가 HTTP 표현에 종속되지 않도록 한다. 향후 입력
 * 필드가 늘어나도 Service 시그니처는 그대로 유지된다.
 */
public record UserUpdateCommand(String nickname, String rawPassword) {}
