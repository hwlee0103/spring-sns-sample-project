package com.lecture.spring_sns_sample_project.domain.post;

import com.lecture.spring_sns_sample_project.domain.common.DomainException;

public class PostException extends DomainException {

  public PostException(String message) {
    super(message);
  }

  public static PostException notFound(Long id) {
    return new PostException("존재하지 않는 게시글입니다: " + id);
  }

  public static PostException invalidField(String fieldName) {
    return new PostException(fieldName + " 값이 비어 있을 수 없습니다.");
  }

  public static PostException contentTooLong() {
    return new PostException("게시글 내용은 " + Post.MAX_CONTENT_LENGTH + "자를 초과할 수 없습니다.");
  }

  public static PostException forbidden(Long id) {
    return new PostException("게시글에 대한 권한이 없습니다: " + id);
  }
}
