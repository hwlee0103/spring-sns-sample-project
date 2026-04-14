package com.lecture.spring_sns_sample_project.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Rate limiting 관련 커스텀 프로퍼티. */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(int ipRequestsPerMinute, int maxBuckets, Duration bucketExpiry) {

  public RateLimitProperties {
    if (ipRequestsPerMinute <= 0) {
      ipRequestsPerMinute = 20;
    }
    if (maxBuckets <= 0) {
      maxBuckets = 10_000;
    }
    if (bucketExpiry == null) {
      bucketExpiry = Duration.ofMinutes(10);
    }
  }
}
