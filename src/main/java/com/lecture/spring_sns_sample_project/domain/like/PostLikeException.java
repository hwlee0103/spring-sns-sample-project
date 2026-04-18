package com.lecture.spring_sns_sample_project.domain.like;

import com.lecture.spring_sns_sample_project.config.ErrorType;
import com.lecture.spring_sns_sample_project.domain.common.DomainException;

public class PostLikeException extends DomainException {

  private PostLikeException(ErrorType errorType, String message) {
    super(errorType, message);
  }

  public static PostLikeException duplicateLike() {
    return new PostLikeException(ErrorType.CONFLICT, "이미 좋아요한 게시글입니다.");
  }

  public static PostLikeException notLiked() {
    return new PostLikeException(ErrorType.BAD_REQUEST, "좋아요하지 않은 게시글입니다.");
  }

  public static PostLikeException invalidField(String fieldName) {
    return new PostLikeException(ErrorType.BAD_REQUEST, fieldName + " 값이 비어 있을 수 없습니다.");
  }
}
