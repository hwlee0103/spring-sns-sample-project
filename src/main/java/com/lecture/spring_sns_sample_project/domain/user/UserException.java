package com.lecture.spring_sns_sample_project.domain.user;

import com.lecture.spring_sns_sample_project.domain.common.DomainException;

/**
 * 사용자 도메인 예외.
 *
 * <p>외부에서는 정적 팩토리 메서드만 사용해야 한다 — 메시지 포맷을 단일 진입점에 모아 일관성을 보장한다.
 */
public class UserException extends DomainException {

  private UserException(String message) {
    super(message);
  }

  public static UserException emailAlreadyExists(String email) {
    return new UserException("이미 존재하는 이메일입니다: " + email);
  }

  public static UserException notFound(Long id) {
    return new UserException("존재하지 않는 사용자입니다: " + id);
  }

  public static UserException notFoundByEmail(String email) {
    return new UserException("존재하지 않는 사용자입니다: " + email);
  }

  public static UserException invalidField(String fieldName) {
    return new UserException(fieldName + " 값이 비어 있을 수 없습니다.");
  }
}
