package com.lecture.spring_sns_sample_project.config;

import com.lecture.spring_sns_sample_project.domain.common.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<Map<String, String>> handleDomainException(DomainException e) {
    return ResponseEntity.status(e.getErrorType().getStatus())
        .body(Map.of("message", e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationException(
      MethodArgumentNotValidException e) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", "요청 값이 올바르지 않습니다.");
    body.put("errors", fieldErrors);
    return ResponseEntity.badRequest().body(body);
  }
}
