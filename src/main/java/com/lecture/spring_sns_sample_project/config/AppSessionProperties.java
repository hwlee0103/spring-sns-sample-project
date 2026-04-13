package com.lecture.spring_sns_sample_project.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 세션 관련 커스텀 프로퍼티. */
@ConfigurationProperties(prefix = "app.session")
public record AppSessionProperties(int maxSessionsPerUser, Duration absoluteTimeout) {

  public AppSessionProperties {
    if (maxSessionsPerUser <= 0) {
      maxSessionsPerUser = 3;
    }
    if (absoluteTimeout == null) {
      absoluteTimeout = Duration.ofHours(24);
    }
  }
}
