package com.lecture.spring_sns_sample_project.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

/**
 * Spring Session + Redis 설정.
 *
 * <p>Boot auto-config 가 {@code spring.session.redis.repository-type=indexed} 프로퍼티로 {@link
 * org.springframework.session.data.redis.RedisIndexedSessionRepository} 를 활성화한다. 이 클래스에서는 {@link
 * SpringSessionBackedSessionRegistry} 빈만 등록하여 Spring Security 의 동시 세션 제어에 연결한다.
 *
 * <p>테스트 환경({@code store-type=none}) 에서는 {@link FindByIndexNameSessionRepository} 빈이 없으므로 이 설정이 자동
 * 비활성화된다.
 */
@Configuration
@ConditionalOnBean(FindByIndexNameSessionRepository.class)
public class SessionConfig<S extends Session> {

  private final FindByIndexNameSessionRepository<S> sessionRepository;

  public SessionConfig(FindByIndexNameSessionRepository<S> sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  @Bean
  public SpringSessionBackedSessionRegistry<S> sessionRegistry() {
    return new SpringSessionBackedSessionRegistry<>(sessionRepository);
  }
}
