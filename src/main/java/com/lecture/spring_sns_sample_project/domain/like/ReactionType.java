package com.lecture.spring_sns_sample_project.domain.like;

/**
 * 리액션 종류. 현재는 LIKE 1종만 사용.
 *
 * <p>향후 LinkedIn/Facebook 스타일 다종 리액션 확장 시 enum 값만 추가. {@code @Enumerated(STRING)} 저장이므로 DB 스키마 변경
 * 불필요.
 */
public enum ReactionType {
  LIKE
  // 향후 확장:
  // CELEBRATE, SUPPORT, FUNNY, LOVE, INSIGHTFUL, CURIOUS, RECOMMEND
}
