package com.lecture.spring_sns_sample_project.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * IP 기반 rate limiting 필터 — 인증 엔드포인트 대상.
 *
 * <p>무차별 대입(brute force) / credential stuffing 차단. 제한 초과 시 HTTP 429 (Too Many Requests) 반환.
 *
 * <p>운영 환경에서는 IP 기반 한계(프록시 뒤 단일 IP) 를 감안하여 Redis 기반 분산 rate limiter 로 교체 권장.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> RATE_LIMITED_PATHS = Set.of("/api/auth/login", "/api/user");

  private static final int REQUESTS_PER_MINUTE = 10;

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    String method = request.getMethod();

    // POST 요청 + rate-limited 경로만 제한
    if ("POST".equalsIgnoreCase(method) && RATE_LIMITED_PATHS.contains(path)) {
      String clientIp = resolveClientIp(request);
      Bucket bucket = buckets.computeIfAbsent(clientIp, k -> createBucket());
      if (!bucket.tryConsume(1)) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private static Bucket createBucket() {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(REQUESTS_PER_MINUTE)
                .refillGreedy(REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                .build())
        .build();
  }

  private static String resolveClientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
