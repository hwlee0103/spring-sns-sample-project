package com.lecture.spring_sns_sample_project.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
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
    String username = event.getAuthentication().getName();
    audit.info("LOGIN_SUCCESS user={}", username);
  }

  @EventListener
  public void onFailure(AbstractAuthenticationFailureEvent event) {
    String username = event.getAuthentication().getName();
    String reason = event.getException().getClass().getSimpleName();
    audit.warn("LOGIN_FAILURE user={} reason={}", username, reason);
  }

  @EventListener
  public void onLogout(LogoutSuccessEvent event) {
    String username =
        event.getAuthentication() != null ? event.getAuthentication().getName() : "unknown";
    audit.info("LOGOUT user={}", username);
  }
}
