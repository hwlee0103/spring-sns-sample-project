# View Count Design — 조회수 설계

> 이 문서는 게시글 조회수의 실시간 카운팅, Redis 캐싱, 배치 동기화 전략을 정의한다.

## 1. 문제 인식

### 1.1 DB 직접 UPDATE 방식의 한계

```
❌ 순진한 구현: 매 조회마다 DB UPDATE

[사용자 A] GET /api/v1/post/42 → UPDATE posts SET view_count = view_count + 1 WHERE id = 42
[사용자 B] GET /api/v1/post/42 → UPDATE posts SET view_count = view_count + 1 WHERE id = 42
[사용자 C] GET /api/v1/post/42 → UPDATE posts SET view_count = view_count + 1 WHERE id = 42
... (초당 수천 건)
```

| 문제 | 설명 |
|------|------|
| **모든 읽기가 쓰기를 유발** | GET 요청이 UPDATE 를 발생시켜 읽기 전용 최적화(캐시, 복제본) 무효화 |
| **디스크 I/O 병목** | 매 UPDATE 마다 WAL 기록 + fsync. 인기 게시글은 초당 수천 행 잠금 |
| **트랜잭션 경합** | 동일 행에 다수 UPDATE 가 직렬화 대기. 응답 지연 증가 |
| **불필요한 정확성** | 조회수 42,381 vs 42,395 — 사용자에게 차이 없음 |

### 1.2 핵심 인사이트

> **조회수는 실시간 정확성이 불필요하다.** 1분 정도의 지연은 UX 에 전혀 영향이 없다.
> Twitter/X 는 조회수를 "Impressions" 라 부르며 지연된 근사값을 표시한다.

## 2. 솔루션: Redis 캐싱 + 배치 동기화

### 2.1 아키텍처

```
[사용자 조회]
    ↓ GET /api/v1/post/42
[PostService.getById()]
    ├── Post 조회 (DB READ — 기존 그대로)
    └── Redis INCR post:views:42 (메모리 쓰기, ~0.1ms)
        → DB UPDATE 없음!

[배치 스케줄러 — 1분 주기]
    ├── Redis SCAN post:views:*
    ├── 각 key 에 대해 GETDEL (값 가져오고 삭제)
    └── UPDATE posts SET view_count = view_count + {delta} WHERE id = ?
        → 1분간 누적된 delta 를 한 번에 반영
        → 초당 1000 조회 × 60초 = 60,000 → 1회 UPDATE 로 압축
```

### 2.2 전후 비교

| 지표 | DB 직접 UPDATE | Redis + 배치 |
|------|---------------|-------------|
| DB UPDATE 빈도 | 조회 1건당 1회 | **1분당 1회** (게시글당) |
| 초당 1000 조회 시 | 1000 UPDATE/초 | **~1 UPDATE/분** |
| 응답 지연 영향 | UPDATE 대기 (5~50ms) | Redis INCR (~0.1ms) |
| DB CPU 부하 | 높음 (행 잠금 경합) | **무시** |
| 조회수 정확도 | 실시간 정확 | **최대 1분 지연** (허용) |
| 장애 시 손실 | 없음 | 최대 1분치 조회수 (허용) |

## 3. Redis 키 설계 — Dirty Set 패턴

### 3.1 핵심 아이디어

단순 KEYS/SCAN 으로 조회수가 변한 게시글을 찾는 것은 비효율적이다. **Dirty Set** 에 변경된 게시글 ID 만 추적하면 동기화 대상을 O(1) 로 식별할 수 있다.

```
Redis 에 2종류의 키 사용:

1. post:views:{postId}     — 게시글별 조회수 delta (INCR 대상)
   타입: String (counter)
   값: 마지막 동기화 이후 누적 조회 수

2. post:views:dirty         — 조회수가 변한 게시글 ID 집합 (SADD 대상)
   타입: Set
   값: {42, 108, 235, ...} — 동기화 필요한 postId 들
```

### 3.2 키 설계 상세

| 키 | 타입 | 명령 | 동시성 | 설명 |
|----|------|------|--------|------|
| `post:views:{postId}` | String | `INCR` | **원자적** — 동시 INCR 이 순차 실행됨. 100 스레드가 동시 INCR 해도 정확히 100 증가 | 조회수 delta |
| `post:views:dirty` | Set | `SADD` | **멱등** — 동일 postId 를 여러 번 SADD 해도 Set 에 1개만 존재. 중복 안전 | 변경 목록 |

> **왜 INCR 이 동시성에 안전한가**: Redis 는 싱글 스레드로 명령을 순차 실행한다. `INCR` 은 단일 명령이므로 atomic 이 보장된다. 100개 요청이 동시에 도착해도 순차 실행되어 정확히 100 증가.
>
> **왜 SADD 가 중복에 안전한가**: Redis Set 은 중복을 허용하지 않는다. `SADD post:views:dirty 42` 를 100번 호출해도 Set 에는 `42` 가 1개만 존재한다. 동기화 시 각 postId 를 정확히 1번만 처리하면 된다.

### 3.3 조회 발생 시 — 2단계 기록

```
[사용자 조회] GET /api/v1/post/42
                ↓
[ViewCountRecorder.increment(42)]
  Step 1: INCR post:views:42           → 조회수 delta + 1 (원자적)
  Step 2: SADD post:views:dirty 42     → dirty set 에 postId 등록 (멱등)
  → DB 쓰기 없음. Redis 명령 2개 (파이프라인으로 1 RTT)
```

### 3.4 조회수 읽기 전략

| 방법 | 구현 | 정확도 | 비용 | 권장 |
|------|------|--------|------|------|
| DB 값만 사용 | `Post.viewCount` | 최대 1분 지연 | 없음 | **✅ 권장** |
| DB + Redis 합산 | `Post.viewCount + GET post:views:{id}` | 실시간 | Redis GET 추가 | 정확도 필요 시 |
| Redis 전용 | 누적 총 조회수를 Redis 에 유지 | 실시간 | Redis 의존 | 비권장 |

> **권장: DB 값만 사용** — 1분 지연은 UX 에 영향 없고, 추가 Redis 호출 없이 기존 Post 쿼리로 충분.

## 4. 배치 스케줄러 설계 — Dirty Set 동기화

### 4.1 배치 주기 권장

| 주기 | DB UPDATE 빈도 | 적합 환경 |
|------|---------------|----------|
| 10초 | 높음 (6회/분) | 실시간 랭킹 |
| 30초 | 중간 | 중간 규모 |
| **1분 (권장)** | 최소 | **일반 SNS** |
| 5분 | 거의 없음 | 트래픽 낮은 서비스 |

> **1분 권장**: Twitter/YouTube 도 분 단위 갱신. "조회수 42,381" 을 보고 새로고침해도 1분 내 같은 값인 것은 자연스러움.

### 4.2 동기화 흐름 — GETDEL + SREM 패턴

```
[배치 스케줄러 — 1분 주기]

Step 1: SMEMBERS post:views:dirty
  → {42, 108, 235} — 조회수가 바뀐 게시글 ID 목록

Step 2: 각 postId 에 대해:
  2-a: GETDEL post:views:42       → delta = 157 (가져오면서 삭제 — 원자적)
  2-b: UPDATE posts SET view_count = view_count + 157 WHERE id = 42

Step 3: SREM post:views:dirty 42  → dirty set 에서 제거
  → 처리 완료된 postId 만 제거
```

### 4.3 동시성 경합 — GETDEL 과 SREM 사이의 갭

```
Timeline:
  t=0  [스케줄러] SMEMBERS dirty → {42}
  t=1  [스케줄러] GETDEL post:views:42 → 157 (키 삭제됨)
  t=2  [사용자 C] INCR post:views:42 → 1 (새로운 조회 발생!)
  t=2  [사용자 C] SADD post:views:dirty 42 → dirty set 에 42 다시 추가
  t=3  [스케줄러] UPDATE posts SET view_count += 157
  t=4  [스케줄러] SREM post:views:dirty 42 → 42 제거됨!

  ⚠️ t=2 의 조회수 "1" 은 post:views:42 에 남아있지만,
     dirty set 에서 42 가 제거되어 다음 동기화까지 인식 안 됨
  → 다음 조회가 올 때 다시 SADD 되므로 결국 동기화됨
  → 최악의 경우: 마지막 1건이 1분 더 지연 (2분 지연)
```

### 4.4 오차 최소화 — 권장 방안 3가지

| 방안 | 설명 | 오차 | 복잡성 |
|------|------|------|--------|
| **A. GETDEL 후 SREM (현재)** | 위 흐름 그대로. 마지막 1건이 다음 주기까지 지연 가능 | 최대 +1분 | 낮음 |
| **B. Lua 스크립트 원자 처리** | GETDEL + SREM 을 Lua 스크립트로 원자적 실행 → 갭 제거 | **0** | 중간 |
| **C. Double-buffer (스왑 패턴)** | dirty set 을 2개 운용, 스왑하여 새 조회와 동기화를 분리 | **0** | 높음 |

#### 방안 B — Lua 스크립트 (권장)

```lua
-- view_sync.lua: GETDEL + SREM 원자적 실행
-- KEYS[1] = post:views:{postId}
-- KEYS[2] = post:views:dirty
-- ARGV[1] = postId
local delta = redis.call('GETDEL', KEYS[1])
if delta then
    redis.call('SREM', KEYS[2], ARGV[1])
end
return delta
```

```java
// Lua 스크립트로 GETDEL + SREM 원자적 실행 — 갭 없음
String delta = redisTemplate.execute(viewSyncScript,
    List.of(VIEW_KEY_PREFIX + postId, DIRTY_SET_KEY),
    String.valueOf(postId));
```

> **Lua 스크립트는 Redis 에서 원자적으로 실행된다.** GETDEL 과 SREM 사이에 다른 명령이 끼어들 수 없으므로, 위의 t=2 경합이 발생하지 않는다.

#### 방안 C — Double-buffer (참고)

```
[주기 시작]
  1. RENAME post:views:dirty → post:views:dirty:processing
     → 이 순간 새 조회는 post:views:dirty (빈 set) 에 쌓임
  2. SMEMBERS post:views:dirty:processing → 처리 대상
  3. 각 postId: GETDEL + DB UPDATE
  4. DEL post:views:dirty:processing
```

> RENAME 은 O(1) 원자적 — 새 조회가 processing set 에 끼어들 가능성 제거. 단, 키 관리가 복잡.

### 4.5 최종 구현 — 방안 B (Lua 스크립트)

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewCountSyncScheduler {

    private final StringRedisTemplate redisTemplate;
    private final PostRepository postRepository;

    private static final String VIEW_KEY_PREFIX = "post:views:";
    private static final String DIRTY_SET_KEY = "post:views:dirty";

    // Lua: GETDEL + SREM 원자적 실행
    private static final RedisScript<String> SYNC_SCRIPT = RedisScript.of(
        "local delta = redis.call('GETDEL', KEYS[1])\n" +
        "if delta then redis.call('SREM', KEYS[2], ARGV[1]) end\n" +
        "return delta",
        String.class);

    @Scheduled(fixedRate = 60_000)  // 1분
    @Transactional
    public void syncViewCounts() {
        // Step 1: dirty set 에서 변경된 게시글 ID 목록 조회
        Set<String> dirtyPostIds = redisTemplate.opsForSet().members(DIRTY_SET_KEY);
        if (dirtyPostIds == null || dirtyPostIds.isEmpty()) return;

        int synced = 0;
        for (String postIdStr : dirtyPostIds) {
            Long postId = Long.parseLong(postIdStr);

            // Step 2: Lua 로 GETDEL + SREM 원자적 실행
            String deltaStr = redisTemplate.execute(
                SYNC_SCRIPT,
                List.of(VIEW_KEY_PREFIX + postId, DIRTY_SET_KEY),
                postIdStr);
            if (deltaStr == null) continue;

            long delta = Long.parseLong(deltaStr);
            if (delta <= 0) continue;

            // Step 3: DB 원자적 UPDATE
            postRepository.incrementViewCountBy(postId, delta);
            synced++;
        }

        if (synced > 0) {
            log.info("[ViewCountSync] {}개 게시글 조회수 동기화 완료", synced);
        }
    }
}
```

### 4.6 ViewCountRecorder — 조회 기록

```java
@Component
@RequiredArgsConstructor
public class ViewCountRecorder {

    private final StringRedisTemplate redisTemplate;
    private static final String VIEW_KEY_PREFIX = "post:views:";
    private static final String DIRTY_SET_KEY = "post:views:dirty";

    /**
     * 조회수 기록 — Redis 에만 기록, DB 쓰기 없음.
     *
     * <p>INCR + SADD 를 파이프라인으로 1 RTT 에 실행.
     * 두 명령 모두 원자적(INCR) / 멱등(SADD) 이므로 동시성 안전.
     */
    public void increment(Long postId) {
        try {
            String postIdStr = String.valueOf(postId);
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                byte[] viewKey = (VIEW_KEY_PREFIX + postId).getBytes();
                byte[] dirtyKey = DIRTY_SET_KEY.getBytes();
                connection.stringCommands().incr(viewKey);            // 조회수 +1
                connection.setCommands().sAdd(dirtyKey, postIdStr.getBytes()); // dirty set 등록
                return null;
            });
        } catch (Exception e) {
            // Redis 장애 시 조회수만 누락 — 서비스 영향 없음
            log.warn("[ViewCount] Redis 기록 실패: postId={}", postId, e);
        }
    }
}
```

### 4.7 PostRepository 추가 메서드

```java
/** 조회수 배치 증가 — delta 만큼 한 번에 증가. */
@Modifying
@Query("UPDATE Post p SET p.viewCount = p.viewCount + :delta WHERE p.id = :postId")
int incrementViewCountBy(@Param("postId") Long postId, @Param("delta") long delta);
```

### 4.8 PostService 연결

```java
public Post getById(Long id) {
    Post post = postRepository.findWithAuthorById(id)
        .orElseThrow(() -> PostException.notFound(id));

    // 조회수: DB 대신 Redis 에만 기록 (dirty set 패턴)
    viewCountRecorder.increment(id);

    return post;
}
```

## 5. 장애 시나리오

### 5.1 Redis 장애

```
[Redis DOWN]
    ↓
[ViewCountRecorder.increment()]
    → try-catch → 로그만 남기고 계속
    → 사용자 응답에 영향 없음
    → 조회수만 누락 (Redis 복구 후 재시작)
```

| 시나리오 | 영향 | 복구 |
|---------|------|------|
| Redis 일시 장애 (30초) | 30초간 조회수 미기록 | 자동 복구 — 다음 조회부터 정상 |
| Redis 재시작 (데이터 손실) | 마지막 동기화 이후 조회수 손실 (최대 1분) | 허용 범위 |
| Redis 영구 장애 | 조회수 기록 전면 중단 | DB 직접 UPDATE 폴백 전환 |

### 5.2 배치 스케줄러 장애

```
[Scheduler DOWN]
    → Redis 에 delta 계속 누적
    → DB 의 viewCount 정체 (stale)
    → Scheduler 복구 시 누적분 한 번에 동기화
```

> **안전장치**: Redis 키에 24시간 TTL 설정 → 스케줄러 장기 장애 시에도 메모리 누수 방지.

## 6. 중복 조회 방지 (향후)

현재는 모든 조회를 카운트하지만, 같은 사용자의 반복 조회를 제한할 수 있다.

### 6.1 방식 비교

| 방식 | 구현 | 정확도 | 비용 |
|------|------|--------|------|
| **제한 없음 (현재)** | 모든 조회 카운트 | 부풀려짐 | 없음 |
| **IP + PostId 조합 TTL** | Redis SET `view:seen:{postId}:{ip}` EX 3600 | 중간 | Redis 메모리 |
| **UserId + PostId 조합 TTL** | Redis SET `view:seen:{postId}:{userId}` EX 3600 | 높음 | Redis 메모리 |
| **HyperLogLog** | Redis PFADD `view:hll:{postId}` userId | 근사 (오차 0.81%) | **매우 적은 메모리** |

> **권장 (향후)**: HyperLogLog — 게시글당 12KB 고정 메모리로 수억 명의 고유 조회자를 추적. YouTube 가 사용하는 방식.

```java
// HyperLogLog 기반 고유 조회수 (향후)
public void incrementUnique(Long postId, Long userId) {
    String key = "view:hll:" + postId;
    redisTemplate.opsForHyperLogLog().add(key, String.valueOf(userId));
    // PFCOUNT 로 고유 조회수 조회
}
```

## 7. 대규모 SNS 비교

| 서비스 | 조회수 구현 | 실시간성 | 중복 방지 |
|--------|-----------|---------|----------|
| Twitter/X | 비동기 집계 (Impressions) | 지연 (분~시간) | 없음 (노출 기반) |
| YouTube | Redis + HyperLogLog + 배치 | 실시간 근사 | HyperLogLog (고유 시청) |
| Instagram | 미표시 (내부 지표만) | — | — |
| LinkedIn | 비동기 (Impressions) | 지연 | 없음 |
| Reddit | upvote 기반 (조회수 별도 미표시) | — | — |
| **이 프로젝트** | **Redis INCR + 1분 배치** | **최대 1분 지연** | **없음 (향후 HLL)** |

## 8. 배치 주기 결정 근거

### 8.1 주기별 DB 부하 시뮬레이션

가정: 활성 게시글 10,000개, 평균 초당 10 조회/게시글

| 배치 주기 | DB UPDATE 빈도 | 시간당 UPDATE | 일간 UPDATE |
|----------|---------------|-------------|------------|
| 즉시 (DB 직접) | 100,000/초 | 360,000,000 | **8.64B** |
| 10초 | 1,000/10초 = 100/초 | 360,000 | 8.64M |
| 30초 | 333/30초 ≈ 11/초 | 40,000 | 960K |
| **1분** | 167/60초 ≈ **2.8/초** | **10,000** | **240K** |
| 5분 | 33/5분 ≈ 0.1/초 | 400 | 9.6K |

> **1분 주기**: DB 직접 대비 UPDATE 횟수 **99.997% 감소** (8.64B → 240K). PostgreSQL 이 충분히 처리 가능한 수준.

### 8.2 지연 허용 범위 — UX 관점

| 지연 | 사용자 인식 | 적합 |
|------|-----------|------|
| < 10초 | 실시간으로 느껴짐 | 실시간 경매, 주식 |
| **30초~2분** | **자연스러움** | **SNS 조회수** |
| 5~10분 | 느리지만 수용 가능 | 통계 대시보드 |
| 1시간+ | 눈에 띄는 지연 | 일간 리포트 |

> **결론**: SNS 조회수는 30초~2분 지연이 자연스러움. **1분** 은 최적의 균형점.

## 9. 구현 순서

| 단계 | 내용 | 상태 |
|------|------|------|
| 1 | `ViewCountRecorder` — Redis INCR 래퍼 | ⬜ |
| 2 | `PostRepository.incrementViewCountBy(postId, delta)` | ⬜ |
| 3 | `ViewCountSyncScheduler` — @Scheduled 1분 배치 | ⬜ |
| 4 | `PostService.getById()` 에 viewCountRecorder.increment() 연결 | ⬜ |
| 5 | 중복 조회 방지 (HyperLogLog) | ⬜ 향후 |
| 6 | 모니터링 (동기화 지연, Redis 메모리) | ⬜ 향후 |

> **현재 상태**: `PostRepository.incrementViewCount(postId)` 는 DB 직접 UPDATE 로 이미 구현되어 있으나 호출되지 않음. Redis 버퍼 도입 시 이 메서드를 `incrementViewCountBy(postId, delta)` 로 대체.
