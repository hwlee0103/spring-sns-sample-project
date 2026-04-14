package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.post.Post;
import java.time.Instant;

public record PostResponse(Long id, String content, UserSummaryResponse author, Instant createdAt) {

  public static PostResponse from(Post post) {
    return new PostResponse(
        post.getId(),
        post.getContent(),
        UserSummaryResponse.from(post.getAuthor()),
        post.getCreatedAt());
  }
}
