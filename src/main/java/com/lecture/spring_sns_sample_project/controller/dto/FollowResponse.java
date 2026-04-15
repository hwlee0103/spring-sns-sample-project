package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.follow.Follow;
import java.time.Instant;

public record FollowResponse(
    Long id, UserSummaryResponse follower, UserSummaryResponse following, Instant createdAt) {

  public static FollowResponse from(Follow follow) {
    return new FollowResponse(
        follow.getId(),
        UserSummaryResponse.from(follow.getFollower()),
        UserSummaryResponse.from(follow.getFollowing()),
        follow.getCreatedAt());
  }
}
