package com.lecture.spring_sns_sample_project.config;

import org.springframework.http.HttpStatus;

/**
 * 도메인 에러 분류와 HTTP 상태 매핑.
 *
 * <p>도메인 레이어가 직접 HttpStatus 에 의존하지 않도록, 도메인 의미(분류) → HTTP 상태 매핑을 한 곳에 모은다. GlobalExceptionHandler 가
 * 이 enum 의 status 를 사용하여 응답한다.
 */
public enum ErrorType {
  BAD_REQUEST(HttpStatus.BAD_REQUEST),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  CONFLICT(HttpStatus.CONFLICT),
  FORBIDDEN(HttpStatus.FORBIDDEN);

  private final HttpStatus status;

  ErrorType(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus getStatus() {
    return status;
  }
}
