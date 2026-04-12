package com.lecture.spring_sns_sample_project.domain.post;

import com.lecture.spring_sns_sample_project.config.ErrorType;
import com.lecture.spring_sns_sample_project.domain.common.DomainException;

public class PostException extends DomainException {

  private PostException(ErrorType errorType, String message) {
    super(errorType, message);
  }

  public static PostException notFound(Long id) {
    return new PostException(ErrorType.NOT_FOUND, "존재하지 않는 게시글입니다: " + id);
  }

  public static PostException invalidField(String fieldName) {
    return new PostException(ErrorType.BAD_REQUEST, fieldName + " 값이 비어 있을 수 없습니다.");
  }

  public static PostException contentTooLong() {
    return new PostException(
        ErrorType.BAD_REQUEST, "게시글 내용은 " + Post.MAX_CONTENT_LENGTH + "자를 초과할 수 없습니다.");
  }

  public static PostException forbidden(Long id) {
    return new PostException(ErrorType.FORBIDDEN, "게시글에 대한 권한이 없습니다: " + id);
  }
}
