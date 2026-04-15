package com.lecture.spring_sns_sample_project.domain.follow;

import com.lecture.spring_sns_sample_project.config.ErrorType;
import com.lecture.spring_sns_sample_project.domain.common.DomainException;

public class FollowException extends DomainException {

  private FollowException(ErrorType errorType, String message) {
    super(errorType, message);
  }

  public static FollowException selfFollow() {
    return new FollowException(ErrorType.BAD_REQUEST, "자기 자신을 팔로우할 수 없습니다.");
  }

  public static FollowException alreadyFollowing() {
    return new FollowException(ErrorType.CONFLICT, "이미 팔로우한 사용자입니다.");
  }

  public static FollowException notFollowing() {
    return new FollowException(ErrorType.BAD_REQUEST, "팔로우하지 않은 사용자입니다.");
  }

  public static FollowException userNotFound(Long id) {
    return new FollowException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다.");
  }

  public static FollowException invalidField(String fieldName) {
    return new FollowException(ErrorType.BAD_REQUEST, fieldName + " 값이 비어 있을 수 없습니다.");
  }
}
