package com.lecture.spring_sns_sample_project.domain.common;

/** 도메인 계층에서 발생하는 비즈니스 예외의 공통 부모 클래스. 각 도메인의 *Exception 클래스는 이 클래스를 상속받아 구현한다. */
public abstract class DomainException extends RuntimeException {

  protected DomainException(String message) {
    super(message);
  }
}
