package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.like.PostLike;
import com.lecture.spring_sns_sample_project.domain.like.ReactionType;
import java.time.Instant;

public record PostLikeResponse(
    Long id, Long userId, Long postId, ReactionType reaction, Instant createdAt) {

  public static PostLikeResponse from(PostLike postLike) {
    return new PostLikeResponse(
        postLike.getId(),
        postLike.getUserId(),
        postLike.getPostId(),
        postLike.getReaction(),
        postLike.getCreatedAt());
  }
}
