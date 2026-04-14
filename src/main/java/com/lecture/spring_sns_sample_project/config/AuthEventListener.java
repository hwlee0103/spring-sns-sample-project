package com.lecture.spring_sns_sample_project.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.stereotype.Component;

/**
 * 인증 이벤트 감사(audit) 로깅.
 *
 * <p>로그인 성공/실패, 로그아웃을 별도 로거로 기록하여 침해 사고 분석의 기반을 마련한다. 운영 환경에서는 ELK/Splunk 등 외부 SIEM 으로 수집 권장.
 */
@Component
public class AuthEventListener {

  private static final Logger audit = LoggerFactory.getLogger("audit.auth");

  @EventListener
  public void onSuccess(AuthenticationSuccessEvent event) {
    audit.info("LOGIN_SUCCESS user={}", sanitize(event.getAuthentication().getName()));
  }

  @EventListener
  public void onFailure(AbstractAuthenticationFailureEvent event) {
    String username = sanitize(event.getAuthentication().getName());
    String reason = event.getException().getClass().getSimpleName();
    audit.warn("LOGIN_FAILURE user={} reason={}", username, reason);
  }

  @EventListener
  public void onLogout(LogoutSuccessEvent event) {
    String username =
        event.getAuthentication() != null
            ? sanitize(event.getAuthentication().getName())
            : "unknown";
    audit.info("LOGOUT user={}", username);
  }

  @EventListener
  public void onSessionCreated(SessionCreatedEvent event) {
    audit.info("SESSION_CREATED sessionId={}", truncateSessionId(event.getSessionId()));
  }

  @EventListener
  public void onSessionDeleted(SessionDeletedEvent event) {
    audit.info("SESSION_DELETED sessionId={}", truncateSessionId(event.getSessionId()));
  }

  @EventListener
  public void onSessionExpired(SessionExpiredEvent event) {
    audit.info("SESSION_EXPIRED sessionId={}", truncateSessionId(event.getSessionId()));
  }

  /** 로그 injection 방어 — 개행/탭 문자를 제거하여 가짜 로그 라인 삽입을 차단. */
  private static String sanitize(String input) {
    if (input == null) {
      return "null";
    }
    return input.replaceAll("[\\r\\n\\t]", "_");
  }

  /** 세션 ID 전체 노출은 로그 유출 시 세션 하이재킹 벡터가 된다. 앞 8자만 기록. */
  private static String truncateSessionId(String sessionId) {
    if (sessionId == null || sessionId.length() <= 8) {
      return sessionId;
    }
    return sessionId.substring(0, 8) + "...";
  }
}
