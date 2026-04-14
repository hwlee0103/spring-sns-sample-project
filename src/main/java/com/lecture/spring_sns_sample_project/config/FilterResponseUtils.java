package com.lecture.spring_sns_sample_project.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 필터에서 JSON 에러 응답을 작성하기 위한 유틸리티. Controller 밖에서 동일한 응답 형식을 보장한다. */
public final class FilterResponseUtils {

  private FilterResponseUtils() {}

  /** JSON {@code {"message":"..."}} 형태의 에러 응답을 작성한다. */
  public static void writeJsonError(HttpServletResponse response, int status, String message)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"message\":\"" + message + "\"}");
  }
}
