package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.post.Post;
import com.lecture.spring_sns_sample_project.domain.post.PostType;
import java.time.Instant;

public record PostResponse(
    Long id,
    String content,
    UserSummaryResponse author,
    PostType type,
    Long parentId,
    Long quoteId,
    Long repostId,
    long replyCount,
    long repostCount,
    long likeCount,
    long viewCount,
    long shareCount,
    boolean editable,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt) {

  public static PostResponse from(Post post) {
    return new PostResponse(
        post.getId(),
        post.getContent(),
        UserSummaryResponse.from(post.getAuthor()),
        post.getType(),
        post.getParentId(),
        post.getQuoteId(),
        post.getRepostId(),
        post.getReplyCount(),
        post.getRepostCount(),
        post.getLikeCount(),
        post.getViewCount(),
        post.getShareCount(),
        post.isEditable(),
        post.isDeleted(),
        post.getCreatedAt(),
        post.getUpdatedAt());
  }
}
