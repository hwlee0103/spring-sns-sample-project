package com.lecture.spring_sns_sample_project.config;

import java.util.Collections;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;

/**
 * 테스트 환경에서 Redis 없이 동작하도록 인메모리 세션 저장소를 제공.
 *
 * <p>{@code spring.session.store-type=none} 과 함께 사용하여 Redis 의존성 없이 컨텍스트를 로드한다. {@link
 * SessionConfig} 가 이 빈을 감지하여 {@code SpringSessionBackedSessionRegistry} 를 자동 등록한다.
 */
@TestConfiguration
public class TestSessionConfig {

  @Bean
  public FindByIndexNameSessionRepository<MapSession> findByIndexNameSessionRepository() {
    return new InMemoryFindByIndexNameSessionRepository();
  }

  /** 테스트 전용 — findByPrincipalName 은 빈 맵을 반환하는 stub. */
  private static class InMemoryFindByIndexNameSessionRepository
      implements FindByIndexNameSessionRepository<MapSession> {

    private final java.util.concurrent.ConcurrentHashMap<String, MapSession> sessions =
        new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public Map<String, MapSession> findByIndexNameAndIndexValue(
        String indexName, String indexValue) {
      return Collections.emptyMap();
    }

    @Override
    public MapSession createSession() {
      return new MapSession();
    }

    @Override
    public void save(MapSession session) {
      sessions.put(session.getId(), session);
    }

    @Override
    public MapSession findById(String id) {
      return sessions.get(id);
    }

    @Override
    public void deleteById(String id) {
      sessions.remove(id);
    }
  }
}
