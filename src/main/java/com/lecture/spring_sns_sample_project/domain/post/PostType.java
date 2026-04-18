package com.lecture.spring_sns_sample_project.domain.post;

/**
 * 게시글 유형 — DB 에 저장하지 않고 parentId/quoteId/repostId 조합에서 유도.
 *
 * <p>판정 우선순위: REPOST > QUOTE > REPLY > ORIGINAL.
 */
public enum PostType {
  ORIGINAL,
  REPLY,
  QUOTE,
  REPOST;

  public static PostType of(Post post) {
    if (post.getRepostId() != null) return REPOST;
    if (post.getQuoteId() != null) return QUOTE;
    if (post.getParentId() != null) return REPLY;
    return ORIGINAL;
  }
}
