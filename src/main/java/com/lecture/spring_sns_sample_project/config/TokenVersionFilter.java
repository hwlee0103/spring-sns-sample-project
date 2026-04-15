package com.lecture.spring_sns_sample_project.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lecture.spring_sns_sample_project.domain.user.UserRepository;
import com.lecture.spring_sns_sample_project.domain.user.security.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 매 인증 요청에서 세션의 tokenVersion 과 DB 의 tokenVersion 을 비교하여, 불일치 시 세션을 무효화한다.
 *
 * <p>Redis 세션 삭제({@code findByPrincipalName})가 인덱스 누락(spring-session#3453) 등으로 실패할 경우의 fallback
 * 안전장치. 비밀번호 변경 후 공격자가 {@code /api/auth/me} 를 호출하지 않아도 모든 경로에서 탈취 세션을 차단한다.
 *
 * <p>DB 부하를 줄이기 위해 {@code userId → tokenVersion} 을 Caffeine 캐시(30초 TTL)로 관리한다. 비밀번호 변경 시 {@link
 * #evict(Long)} 을 호출하면 즉시 무효화된다.
 *
 * <p><b>다중 인스턴스 주의</b>: Caffeine 은 로컬 인메모리 캐시이므로, 비밀번호 변경 시 {@link #evict(Long)} 은 현재 인스턴스에서만 동작한다.
 * 다른 인스턴스의 캐시는 TTL(30초) 만큼 stale 할 수 있다. 1차 방어인 Redis 세션 삭제({@code invalidateOtherSessions})가 대부분의
 * 경우를 처리하며, 이 필터는 인덱스 누락 시의 fallback 이므로 30초 지연은 수용 가능하다.
 */
public class TokenVersionFilter extends OncePerRequestFilter {

  private final UserRepository userRepository;
  private final Cache<Long, Integer> tokenVersionCache;

  public TokenVersionFilter(UserRepository userRepository) {
    this.userRepository = userRepository;
    this.tokenVersionCache =
        Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(Duration.ofSeconds(30)).build();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    AuthUser authUser = getAuthUser();
    if (authUser != null) {
      int currentVersion =
          tokenVersionCache.get(
              authUser.getId(),
              id ->
                  userRepository
                      .findById(id)
                      .map(user -> user.getTokenVersion())
                      .orElse(-1)); // 삭제된 사용자 → 불일치 유도

      if (authUser.getTokenVersion() != currentVersion) {
        HttpSession session = request.getSession(false);
        if (session != null) {
          session.invalidate();
        }
        SecurityContextHolder.clearContext();
        FilterResponseUtils.writeJsonError(
            response, HttpServletResponse.SC_UNAUTHORIZED, "세션이 만료되었습니다.");
        return;
      }
    }

    filterChain.doFilter(request, response);
  }

  /** 비밀번호 변경 시 호출하여 해당 사용자의 캐시를 즉시 무효화한다. */
  public void evict(Long userId) {
    tokenVersionCache.invalidate(userId);
  }

  private static AuthUser getAuthUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
      return authUser;
    }
    return null;
  }
}
