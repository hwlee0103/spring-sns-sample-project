package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.follow.Follow;
import java.time.Instant;

/** 팔로워/팔로잉 목록 각 항목 — 사용자 요약 + 팔로우 시점. */
public record FollowUserResponse(Long id, String nickname, Instant followedAt) {

  /** 팔로워 목록용 — follow.follower 의 정보. */
  public static FollowUserResponse fromFollower(Follow follow) {
    return new FollowUserResponse(
        follow.getFollower().getId(), follow.getFollower().getNickname(), follow.getCreatedAt());
  }

  /** 팔로잉 목록용 — follow.following 의 정보. */
  public static FollowUserResponse fromFollowing(Follow follow) {
    return new FollowUserResponse(
        follow.getFollowing().getId(), follow.getFollowing().getNickname(), follow.getCreatedAt());
  }
}
