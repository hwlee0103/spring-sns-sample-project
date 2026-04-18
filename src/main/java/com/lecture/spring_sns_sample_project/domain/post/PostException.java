package com.lecture.spring_sns_sample_project.domain.post;

import com.lecture.spring_sns_sample_project.config.ErrorType;
import com.lecture.spring_sns_sample_project.domain.common.DomainException;

public class PostException extends DomainException {

  private PostException(ErrorType errorType, String message) {
    super(errorType, message);
  }

  public static PostException notFound(Long id) {
    return new PostException(ErrorType.NOT_FOUND, "존재하지 않는 게시글입니다.");
  }

  public static PostException invalidField(String fieldName) {
    return new PostException(ErrorType.BAD_REQUEST, fieldName + " 값이 비어 있을 수 없습니다.");
  }

  public static PostException authorNotFound(Long authorId) {
    return new PostException(ErrorType.NOT_FOUND, "존재하지 않는 작성자입니다.");
  }

  public static PostException contentTooLong() {
    return new PostException(
        ErrorType.BAD_REQUEST, "게시글 내용은 " + Post.MAX_CONTENT_LENGTH + "자를 초과할 수 없습니다.");
  }

  public static PostException forbidden(Long id) {
    return new PostException(ErrorType.FORBIDDEN, "게시글에 대한 권한이 없습니다.");
  }

  public static PostException editWindowExpired() {
    return new PostException(
        ErrorType.BAD_REQUEST, "게시글 수정은 작성 후 " + Post.EDIT_WINDOW_MINUTES + "분 이내에만 가능합니다.");
  }

  public static PostException replyToDeletedPost() {
    return new PostException(ErrorType.BAD_REQUEST, "삭제된 게시글에는 답글을 작성할 수 없습니다.");
  }

  public static PostException repostDeletedPost() {
    return new PostException(ErrorType.BAD_REQUEST, "삭제된 게시글은 리포스트할 수 없습니다.");
  }

  public static PostException repostOfRepost() {
    return new PostException(ErrorType.BAD_REQUEST, "리포스트는 원본 게시글만 가능합니다.");
  }

  public static PostException duplicateRepost() {
    return new PostException(ErrorType.CONFLICT, "이미 리포스트한 게시글입니다.");
  }

  public static PostException duplicateQuote() {
    return new PostException(ErrorType.CONFLICT, "이미 인용한 게시글입니다.");
  }
}
