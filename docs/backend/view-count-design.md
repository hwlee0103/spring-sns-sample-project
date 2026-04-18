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

## 3. Redis 키 설계

### 3.1 키 패턴

```
post:views:{postId}     — 게시글별 조회수 delta (INCR 대상)
```

- **타입**: String (Redis counter)
- **값**: 마지막 배치 동기화 이후 누적된 조회수 delta
- **TTL**: 없음 (배치에서 GETDEL 로 삭제). 단, 안전장치로 24시간 TTL 권장

### 3.2 조회수 읽기 전략

```
[프론트에서 조회수 표시]

방법 1: DB 값 사용 (권장 — 단순)
  → Post.viewCount (DB 저장값, 최대 1분 지연)
  → 피드 조회 시 JOIN 없이 Post 행에서 바로 읽음

방법 2: DB + Redis 합산 (정확)
  → Post.viewCount + Redis GET post:views:{id}
  → 추가 Redis 호출 필요. 피드 N개면 N번 GET → MGET 으로 최적화

방법 3: Redis 전용 (실시간)
  → Redis 에 누적 총 조회수 유지 (INCRBY 패턴)
  → DB 는 백업 용도만
```

> **권장: 방법 1** — 1분 지연은 UX 에 영향 없고, 추가 Redis 호출 없이 기존 Post 쿼리로 충분.

## 4. 배치 스케줄러 설계

### 4.1 배치 주기 권장

| 주기 | 장점 | 단점 | 적합 환경 |
|------|------|------|----------|
| 10초 | 거의 실시간 | DB UPDATE 빈도 높음 (6회/분) | 실시간 랭킹 필요 시 |
| 30초 | 좋은 균형 | — | 중간 규모 |
| **1분 (권장)** | DB 부하 최소, 대부분의 SNS 허용 범위 | 최대 1분 지연 | **일반 SNS** |
| 5분 | DB 부하 거의 없음 | 5분 지연 체감 가능 | 트래픽 매우 낮은 서비스 |
| 10분+ | — | 지연 체감됨 | 조회수 중요하지 않은 서비스 |

> **권장: 1분** — Twitter/YouTube 도 조회수를 분 단위로 갱신. 사용자가 "조회수 1,234" 를 보고 새로고침해도 1분 내 같은 값이 나오는 것은 자연스러움.

### 4.2 배치 구현 — Spring @Scheduled

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ViewCountSyncScheduler {

    private final StringRedisTemplate redisTemplate;
    private final PostRepository postRepository;

    private static final String VIEW_KEY_PREFIX = "post:views:";

    /**
     * 1분마다 Redis 의 조회수 delta 를 DB 에 동기화.
     * Redis SCAN + GETDEL 패턴으로 원자적으로 값을 가져오고 삭제.
     */
    @Scheduled(fixedRate = 60_000)  // 1분
    @Transactional
    public void syncViewCounts() {
        Set<String> keys = redisTemplate.keys(VIEW_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;

        int synced = 0;
        for (String key : keys) {
            // GETDEL: 값을 가져오면서 동시에 삭제 (원자적)
            String deltaStr = redisTemplate.opsForValue().getAndDelete(key);
            if (deltaStr == null) continue;

            long delta = Long.parseLong(deltaStr);
            if (delta <= 0) continue;

            Long postId = Long.parseLong(key.substring(VIEW_KEY_PREFIX.length()));
            postRepository.incrementViewCountBy(postId, delta);
            synced++;
        }

        if (synced > 0) {
            log.info("[ViewCountSync] {}개 게시글 조회수 동기화 (총 delta 합산)", synced);
        }
    }
}
```

### 4.3 PostRepository 추가 메서드

```java
/** 조회수 배치 증가 — delta 만큼 한 번에 증가. */
@Modifying
@Query("UPDATE Post p SET p.viewCount = p.viewCount + :delta WHERE p.id = :postId")
int incrementViewCountBy(@Param("postId") Long postId, @Param("delta") long delta);
```

### 4.4 PostService 조회수 기록

```java
public Post getById(Long id) {
    Post post = postRepository.findWithAuthorById(id)
        .orElseThrow(() -> PostException.notFound(id));

    // 조회수: DB 대신 Redis 에만 기록 (배치로 동기화)
    viewCountRecorder.increment(id);

    return post;
}
```

```java
@Component
@RequiredArgsConstructor
public class ViewCountRecorder {

    private final StringRedisTemplate redisTemplate;
    private static final String VIEW_KEY_PREFIX = "post:views:";

    /** Redis INCR — O(1), ~0.1ms. DB 쓰기 없음. */
    public void increment(Long postId) {
        try {
            redisTemplate.opsForValue().increment(VIEW_KEY_PREFIX + postId);
        } catch (Exception e) {
            // Redis 장애 시 조회수만 누락 — 서비스 영향 없음
            // 로그만 남기고 진행
        }
    }
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
