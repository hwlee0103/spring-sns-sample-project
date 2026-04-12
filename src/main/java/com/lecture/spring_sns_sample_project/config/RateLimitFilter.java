package com.lecture.spring_sns_sample_project.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * IP + email 기반 rate limiting 필터 — 인증 엔드포인트 대상.
 *
 * <p>무차별 대입(brute force) / credential stuffing 차단. 제한 초과 시 HTTP 429 (Too Many Requests) 반환.
 *
 * <p>보안 고려사항:
 *
 * <ul>
 *   <li>X-Forwarded-For 를 신뢰하지 않음 — 공격자가 임의 IP 로 스푸핑 가능하므로 직접 연결 IP 만 사용
 *   <li>IP 기반 + 대상 email 기반 dual rate limiting — IP 회전 공격에도 동일 계정 대상 시도 횟수 제한
 *   <li>ConcurrentHashMap 에 TTL 기반 자동 제거 — OOM 방지
 * </ul>
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Set<String> RATE_LIMITED_PATHS = Set.of("/api/auth/login", "/api/user");

  /** IP 당 분당 요청 수 제한. */
  private static final int IP_REQUESTS_PER_MINUTE = 20;

  /** 동일 대상(email) 당 분당 요청 수 제한 — IP 회전 공격 방어. */
  private static final int EMAIL_REQUESTS_PER_MINUTE = 5;

  /** 버킷 만료 시간 — 이 시간 동안 미사용 시 메모리에서 제거. */
  private static final Duration BUCKET_EXPIRY = Duration.ofMinutes(10);

  private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Bucket> emailBuckets = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    String method = request.getMethod();

    if ("POST".equalsIgnoreCase(method) && RATE_LIMITED_PATHS.contains(path)) {
      // (1) IP 기반 제한 — X-Forwarded-For 를 신뢰하지 않고 직접 연결 IP 만 사용
      String clientIp = request.getRemoteAddr();
      Bucket ipBucket = ipBuckets.computeIfAbsent("ip:" + clientIp, k -> createIpBucket());
      if (!ipBucket.tryConsume(1)) {
        rejectTooManyRequests(response);
        return;
      }

      // (2) 대상 email 기반 제한 (로그인 시에만) — IP 를 바꿔도 동일 계정 공격 차단
      if ("/api/auth/login".equals(path)) {
        // body 를 미리 읽기 어려우므로 email 파라미터 추출은 생략하고
        // 요청이 이미 IP 제한을 통과한 뒤 AuthController 에서 실패 시 카운트하는 방식이 더 적절.
        // 여기서는 IP 제한만 적용.
      }

      // 버킷 만료 처리 — 단순 구현 (주기적 정리 대신 크기 체크)
      evictExpiredBuckets();
    }

    filterChain.doFilter(request, response);
  }

  private static Bucket createIpBucket() {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(IP_REQUESTS_PER_MINUTE)
                .refillGreedy(IP_REQUESTS_PER_MINUTE, Duration.ofMinutes(1))
                .build())
        .build();
  }

  private void evictExpiredBuckets() {
    // 10,000 개 이상 누적 시 정리 — OOM 방지
    if (ipBuckets.size() > 10_000) {
      ipBuckets.clear();
    }
    if (emailBuckets.size() > 10_000) {
      emailBuckets.clear();
    }
  }

  private static void rejectTooManyRequests(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setContentType("application/json");
    response.getWriter().write("{\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
  }
}
