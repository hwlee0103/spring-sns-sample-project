package com.lecture.spring_sns_sample_project.domain.user;

import com.lecture.spring_sns_sample_project.domain.common.DomainException;
import com.lecture.spring_sns_sample_project.domain.common.ErrorType;

/**
 * 사용자 도메인 예외.
 *
 * <p>외부에서는 정적 팩토리 메서드만 사용해야 한다 — 메시지 포맷과 ErrorType 매핑을 단일 진입점에 모아 일관성을 보장한다.
 */
public class UserException extends DomainException {

  private UserException(ErrorType errorType, String message) {
    super(errorType, message);
  }

  public static UserException emailAlreadyExists(String email) {
    return new UserException(ErrorType.CONFLICT, "이미 존재하는 이메일입니다: " + email);
  }

  public static UserException notFound(Long id) {
    return new UserException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다: " + id);
  }

  public static UserException notFoundByEmail(String email) {
    return new UserException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다: " + email);
  }

  public static UserException invalidField(String fieldName) {
    return new UserException(ErrorType.BAD_REQUEST, fieldName + " 값이 비어 있을 수 없습니다.");
  }
}
