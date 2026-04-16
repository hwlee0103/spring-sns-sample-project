package com.lecture.spring_sns_sample_project.domain.user.security;

import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Redis 세션 정리 서비스.
 *
 * <p>비밀번호 변경/계정 삭제 시점에 특정 principal(email) 의 세션들을 Redis 에서 즉시 제거한다. Controller 에서 인프라(Redis) 로직을
 * 분리하여 재사용성과 테스트 용이성을 높인다.
 *
 * <p>Redis 미사용 환경(= in-memory SessionRegistry 만 동작)에서는 sessionRepository 주입이 없으므로 모든 메서드가 no-op 로
 * 동작한다.
 */
@Service
@RequiredArgsConstructor
public class SessionCleanupService {

  @Nullable private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  /** 해당 principal 의 모든 Redis 세션 삭제. 계정 탈퇴 시 사용. */
  public void invalidateAllSessions(String principal) {
    if (sessionRepository == null) {
      return;
    }
    sessionRepository
        .findByPrincipalName(principal)
        .forEach((sessionId, session) -> sessionRepository.deleteById(sessionId));
  }

  /** 해당 principal 의 세션 중 현재 세션을 제외한 모든 세션 삭제. 비밀번호 변경 시 사용. */
  public void invalidateOtherSessions(String principal, String currentSessionId) {
    if (sessionRepository == null) {
      return;
    }
    sessionRepository
        .findByPrincipalName(principal)
        .forEach(
            (sessionId, session) -> {
              if (!sessionId.equals(currentSessionId)) {
                sessionRepository.deleteById(sessionId);
              }
            });
  }
}
