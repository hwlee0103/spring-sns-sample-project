package com.lecture.spring_sns_sample_project.config;

import com.lecture.spring_sns_sample_project.domain.user.UserException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(UserException.class)
  public ResponseEntity<Map<String, String>> handleUserException(UserException e) {
    return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
  }
}
