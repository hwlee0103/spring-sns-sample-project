package com.lecture.spring_sns_sample_project.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

/**
 * Spring Session 설정.
 *
 * <p>Redis 가 있으면 {@link SpringSessionBackedSessionRegistry} 를, 없으면 인메모리 {@link SessionRegistryImpl}
 * 을 fallback 으로 등록한다.
 */
@Configuration
public class SessionConfig {

  /** Redis 환경 — {@link FindByIndexNameSessionRepository} 가 있을 때. */
  @Configuration
  @ConditionalOnBean(FindByIndexNameSessionRepository.class)
  @RequiredArgsConstructor
  static class RedisSessionConfig<S extends Session> {

    private final FindByIndexNameSessionRepository<S> sessionRepository;

    @Bean
    public SpringSessionBackedSessionRegistry<S> sessionRegistry() {
      return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }
  }

  /** Redis 없는 환경 (dev/test) — 인메모리 fallback. */
  @Bean
  @ConditionalOnMissingBean(SessionRegistry.class)
  public SessionRegistry sessionRegistry() {
    return new SessionRegistryImpl();
  }
}
