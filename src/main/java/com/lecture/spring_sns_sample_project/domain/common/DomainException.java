package com.lecture.spring_sns_sample_project.domain.common;

import com.lecture.spring_sns_sample_project.config.ErrorType;

/**
 * 도메인 계층에서 발생하는 비즈니스 예외의 공통 부모 클래스. 각 도메인의 *Exception 클래스는 이 클래스를 상속받아 구현한다.
 *
 * <p>예외 인스턴스는 분류를 나타내는 {@link ErrorType} 을 함께 보유하며, GlobalExceptionHandler 가 이를 통해 적절한 HTTP 상태 코드를
 * 결정한다.
 */
public abstract class DomainException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorType errorType;

  protected DomainException(ErrorType errorType, String message) {
    super(message);
    this.errorType = errorType;
  }

  public ErrorType getErrorType() {
    return errorType;
  }
}
