package com.lecture.spring_sns_sample_project.domain.media;

import com.lecture.spring_sns_sample_project.config.ErrorType;
import com.lecture.spring_sns_sample_project.domain.common.DomainException;

public class MediaException extends DomainException {

  private MediaException(ErrorType errorType, String message) {
    super(errorType, message);
  }

  public static MediaException notFound(Long id) {
    return new MediaException(ErrorType.NOT_FOUND, "존재하지 않는 미디어입니다.");
  }

  public static MediaException invalidField(String fieldName) {
    return new MediaException(ErrorType.BAD_REQUEST, fieldName + " 값이 비어 있을 수 없습니다.");
  }

  public static MediaException invalidStatusTransition(MediaStatus from, MediaStatus to) {
    return new MediaException(
        ErrorType.BAD_REQUEST, "미디어 상태를 " + from + " 에서 " + to + " 로 변경할 수 없습니다.");
  }

  public static MediaException forbidden() {
    return new MediaException(ErrorType.FORBIDDEN, "미디어에 대한 권한이 없습니다.");
  }

  public static MediaException notCompleted() {
    return new MediaException(ErrorType.BAD_REQUEST, "완료되지 않은 미디어는 게시글에 연결할 수 없습니다.");
  }
}
