package com.lecture.spring_sns_sample_project.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * IP 기반 rate limiting 필터.
 *
 * <p>POST 인증 엔드포인트({@code /api/auth/login}, {@code /api/user})에 대해 IP 당 분당 요청 수를 제한한다. {@code
 * X-Forwarded-For} 는 스푸핑 가능하므로 {@code request.getRemoteAddr()} 만 사용한다.
 *
 * <p>Caffeine 캐시를 사용하여 개별 엔트리가 10분 미사용 시 자동 만료되고, 최대 엔트리 수를 초과하면 가장 오래된 것부터 제거된다 (전체 clear 대신 개별
 * eviction).
 *
 * <p>TODO: 이메일 기반 rate limiting (IP 로테이션 공격 방어) 은 인증 실패 이벤트 기반으로 구현 예정.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> RATE_LIMITED_PATHS = Set.of("/api/auth/login", "/api/user");

  private final int ipRequestsPerMinute;
  private final Cache<String, Bucket> ipBuckets;

  public RateLimitFilter(RateLimitProperties properties) {
    this.ipRequestsPerMinute = properties.ipRequestsPerMinute();
    this.ipBuckets =
        Caffeine.newBuilder()
            .maximumSize(properties.maxBuckets())
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    String method = request.getMethod();

    if ("POST".equalsIgnoreCase(method) && RATE_LIMITED_PATHS.contains(path)) {
      String clientIp = request.getRemoteAddr();
      Bucket ipBucket = ipBuckets.get("ip:" + clientIp, k -> createIpBucket());
      if (!ipBucket.tryConsume(1)) {
        rejectTooManyRequests(response);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  private Bucket createIpBucket() {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(ipRequestsPerMinute)
                .refillGreedy(ipRequestsPerMinute, Duration.ofMinutes(1))
                .build())
        .build();
  }

  private static void rejectTooManyRequests(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType("application/json");
    response.getWriter().write("{\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
  }
}
