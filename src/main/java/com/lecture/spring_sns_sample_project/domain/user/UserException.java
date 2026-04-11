package com.lecture.spring_sns_sample_project.domain.user;

import com.lecture.spring_sns_sample_project.domain.common.DomainException;

public class UserException extends DomainException {

  public UserException(String message) {
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
