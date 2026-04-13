package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.user.User;

/** 공개 엔드포인트용 사용자 요약 응답 — email, role 등 민감 필드를 노출하지 않는다. */
public record UserSummaryResponse(Long id, String nickname) {
  public static UserSummaryResponse from(User user) {
    return new UserSummaryResponse(user.getId(), user.getNickname());
  }
}
