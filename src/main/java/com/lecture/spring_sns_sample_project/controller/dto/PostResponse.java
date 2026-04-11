package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.post.Post;
import java.time.Instant;

public record PostResponse(Long id, String content, UserResponse author, Instant createdAt) {

  public static PostResponse from(Post post) {
    return new PostResponse(
        post.getId(), post.getContent(), UserResponse.from(post.getAuthor()), post.getCreatedAt());
  }
}
