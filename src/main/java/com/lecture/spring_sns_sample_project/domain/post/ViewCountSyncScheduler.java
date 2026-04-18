package com.lecture.spring_sns_sample_project.domain.post;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조회수 배치 동기화 스케줄러 — Dirty Set 패턴.
 *
 * <p>1분마다 Redis dirty set 에서 변경된 게시글 목록을 읽고, 각 게시글의 조회수 delta 를 DB 에 반영한다. Lua 스크립트로 GETDEL + SREM
 * 을 원자적으로 실행하여 동기화 갭을 제거한다.
 *
 * <p>Redis 미사용 환경(dev)에서는 {@code @ConditionalOnBean(StringRedisTemplate.class)} 로 빈 자체가 등록되지 않는다.
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class ViewCountSyncScheduler {

  private final StringRedisTemplate redisTemplate;
  private final PostRepository postRepository;

  /** Lua: GETDEL + SREM 원자적 실행 — 동기화 갭 제거. */
  private static final RedisScript<String> SYNC_SCRIPT =
      RedisScript.of(
          "local delta = redis.call('GETDEL', KEYS[1])\n"
              + "if delta then redis.call('SREM', KEYS[2], ARGV[1]) end\n"
              + "return delta",
          String.class);

  /** 1분마다 Redis 조회수 delta → DB 동기화. */
  @Scheduled(fixedRate = 60_000)
  @Transactional
  public void syncViewCounts() {
    Set<String> dirtyPostIds = redisTemplate.opsForSet().members(ViewCountRecorder.DIRTY_SET_KEY);
    if (dirtyPostIds == null || dirtyPostIds.isEmpty()) {
      return;
    }

    int synced = 0;
    for (String postIdStr : dirtyPostIds) {
      Long postId = Long.parseLong(postIdStr);

      String deltaStr =
          redisTemplate.execute(
              SYNC_SCRIPT,
              List.of(ViewCountRecorder.VIEW_KEY_PREFIX + postId, ViewCountRecorder.DIRTY_SET_KEY),
              postIdStr);
      if (deltaStr == null) {
        continue;
      }

      long delta = Long.parseLong(deltaStr);
      if (delta <= 0) {
        continue;
      }

      postRepository.incrementViewCountBy(postId, delta);
      synced++;
    }

    if (synced > 0) {
      log.info("[ViewCountSync] {}개 게시글 조회수 동기화 완료", synced);
    }
  }
}
