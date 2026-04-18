package com.lecture.spring_sns_sample_project.domain.post;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 게시글 조회수 Redis 기록기 — Dirty Set 패턴.
 *
 * <p>조회 발생 시 DB UPDATE 대신 Redis 에만 기록한다:
 *
 * <ol>
 *   <li>{@code INCR post:views:{postId}} — 조회수 delta +1 (원자적, 동시성 안전)
 *   <li>{@code SADD post:views:dirty {postId}} — 변경 목록 등록 (멱등, 중복 안전)
 * </ol>
 *
 * <p>두 명령을 Pipeline 으로 1 RTT 에 실행. Redis 미사용 환경(dev)에서는 no-op.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewCountRecorder {

  static final String VIEW_KEY_PREFIX = "post:views:";
  static final String DIRTY_SET_KEY = "post:views:dirty";

  @Nullable private final StringRedisTemplate redisTemplate;

  /** 조회수 기록 — Redis INCR + SADD (Pipeline 1 RTT). Redis 없으면 no-op. */
  public void increment(Long postId) {
    if (redisTemplate == null) {
      return;
    }
    try {
      String postIdStr = String.valueOf(postId);
      byte[] viewKey = (VIEW_KEY_PREFIX + postId).getBytes();
      byte[] dirtyKey = DIRTY_SET_KEY.getBytes();
      byte[] postIdBytes = postIdStr.getBytes();

      redisTemplate.executePipelined(
          (RedisConnection connection) -> {
            connection.stringCommands().incr(viewKey);
            connection.setCommands().sAdd(dirtyKey, postIdBytes);
            return null;
          });
    } catch (Exception e) {
      log.warn("[ViewCount] Redis 기록 실패 — 조회수 누락 허용: postId={}", postId, e);
    }
  }
}
