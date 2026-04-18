# Like Service Design — 좋아요(리액션) 도메인 설계

> 이 문서는 좋아요(Like/Reaction) 도메인의 엔티티, 비즈니스 규칙, API 계약, 확장 전략을 정의한다.
> 대규모 SNS(Twitter/X, LinkedIn, Facebook, Instagram)를 참고하여 설계.

## 1. 개요

### 1.1 왜 별도 테이블인가

| 방식 | 장점 | 단점 |
|------|------|------|
| Post 에 liked_user_ids 배열 | 단순 | 대량 좋아요 시 행 크기 폭증, 인덱싱 불가, 동시성 문제 |
| **별도 post_likes 테이블** | 정규화, UNIQUE 제약으로 중복 방지, 사용자별/게시글별 독립 인덱싱, 리액션 확장 용이 | JOIN 필요 (카운트는 비정규화로 해결) |

> **선택**: 별도 테이블 — 게시글과 조회 패턴이 근본적으로 다름 (게시글: 피드 스크롤, 좋아요: 특정 게시글+사용자 존재 확인).

### 1.2 대규모 SNS 리액션 비교

| 서비스 | 리액션 종류 | 구현 |
|--------|-----------|------|
| Twitter/X | Like (♡) 1종 | 단일 boolean |
| Instagram | Like (♡) 1종 | 단일 boolean |
| Facebook | Like + Reaction 7종 (Love/Haha/Wow/Sad/Angry/Care) | reaction enum |
| LinkedIn | Like + Reaction 7종 (Celebrate/Support/Funny/Love/Insightful/Curious) | reaction enum |
| Threads | Like (♡) 1종 | 단일 boolean |
| **이 프로젝트** | **Like 1종 → 향후 Reaction 확장** | **reaction enum (확장 가능)** |

## 2. Entity 설계

### 2.1 필드 정의

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, IDENTITY | — |
| userId | Long | not null | 좋아요를 누른 사용자 ID (FK 제약 없음) |
| postId | Long | not null | 대상 게시글 ID (FK 제약 없음) |
| reaction | ReactionType | not null, default LIKE | 리액션 종류 |
| createdAt | Instant | not null (BaseEntity) | 생성 시각 |
| updatedAt | Instant | not null (BaseEntity) | 수정 시각 (리액션 변경 시) |
| deletedAt | Instant | nullable (BaseEntity) | 좋아요 취소 시각 (soft delete) |

> **userId/postId 를 `Long` 으로 선택한 이유**: Post 의 parentId/quoteId/repostId 와 동일 — `@ManyToOne` 대신 ID 직접 참조. 좋아요 목록 조회 시 User/Post 를 별도 API 로 lazy 로딩. FK 제약 없음 정책과 일관.

### 2.2 UNIQUE 제약

```sql
UNIQUE (user_id, post_id)  -- 한 사용자가 한 게시글에 1개의 리액션만
```

> soft delete(deletedAt) 사용 시 UNIQUE 제약과 충돌 주의: 삭제 후 재좋아요 시 기존 행 복원(Follow 패턴과 동일).

### 2.3 ReactionType enum — 확장 가능 설계

```java
/**
 * 리액션 종류. 현재는 LIKE 1종만 사용.
 * 향후 LinkedIn/Facebook 스타일 다종 리액션 확장 시 enum 값만 추가하면 됨.
 */
public enum ReactionType {
    LIKE,          // ♡ 기본 좋아요
    // --- 향후 확장 ---
    // CELEBRATE,  // 🎉 축하
    // SUPPORT,    // 💪 응원
    // FUNNY,      // 😂 웃김
    // LOVE,       // ❤️ 사랑
    // INSIGHTFUL, // 💡 통찰
    // CURIOUS,    // 🤔 궁금
    // RECOMMEND,  // ✅ 추천
}
```

**확장 시 변경 범위**:

| 변경 | 영향 |
|------|------|
| `ReactionType` enum 값 추가 | Entity/DB 스키마 변경 없음 (`VARCHAR` 로 저장) |
| `Post.likeCount` → `Post.reactionCount` | 단일 카운트 유지 or 리액션별 카운트 분리 (별도 결정) |
| API: `reaction` 파라미터 추가 | `POST /api/v1/post/{id}/like` → `POST /api/v1/post/{id}/reaction?type=LOVE` |
| 프론트: 리액션 피커 UI | 좋아요 버튼 long press → 리액션 선택 팝업 |

> **현재 전략**: LIKE 1종으로 시작. `@Enumerated(STRING)` 으로 저장하여 enum 값 추가만으로 확장 가능. DB 스키마 마이그레이션 불필요.

### 2.4 Entity 코드

```java
@Entity
@Table(name = "post_likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"}),
    indexes = {
        @Index(name = "idx_post_likes_post_id", columnList = "post_id"),
        @Index(name = "idx_post_likes_user_id", columnList = "user_id")
    })
@Getter
public class PostLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReactionType reaction;

    protected PostLike() {}

    public PostLike(Long userId, Long postId) {
        this(userId, postId, ReactionType.LIKE);
    }

    public PostLike(Long userId, Long postId, ReactionType reaction) {
        if (userId == null) throw new IllegalArgumentException("userId는 필수입니다.");
        if (postId == null) throw new IllegalArgumentException("postId는 필수입니다.");
        if (reaction == null) throw new IllegalArgumentException("reaction은 필수입니다.");
        this.userId = userId;
        this.postId = postId;
        this.reaction = reaction;
    }

    /** 리액션 변경 (향후: LIKE → LOVE 등). */
    public void changeReaction(ReactionType newReaction) {
        if (newReaction == null) throw new IllegalArgumentException("reaction은 필수입니다.");
        this.reaction = newReaction;
    }

    /** 좋아요 취소 — soft delete. */
    public void cancel() {
        this.deletedAt = Instant.now();
    }

    /** 좋아요 복원 — 취소 후 재좋아요 시 기존 행 복원. */
    public void restore(ReactionType reaction) {
        this.deletedAt = null;
        this.reaction = reaction;
        this.createdAt = Instant.now();
    }

    public boolean isCancelled() {
        return deletedAt != null;
    }
}
```

## 3. Repository 설계

```java
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    /** 활성 좋아요 조회 (soft delete 제외). */
    @Query("SELECT pl FROM PostLike pl WHERE pl.userId = :userId AND pl.postId = :postId AND pl.deletedAt IS NULL")
    Optional<PostLike> findActiveByUserIdAndPostId(
        @Param("userId") Long userId, @Param("postId") Long postId);

    /** 취소된 좋아요 포함 전체 조회 — 복원용. */
    Optional<PostLike> findByUserIdAndPostId(Long userId, Long postId);

    /** 활성 좋아요 존재 확인. */
    @Query("SELECT CASE WHEN COUNT(pl) > 0 THEN true ELSE false END FROM PostLike pl WHERE pl.userId = :userId AND pl.postId = :postId AND pl.deletedAt IS NULL")
    boolean existsActiveByUserIdAndPostId(
        @Param("userId") Long userId, @Param("postId") Long postId);

    /** 게시글의 좋아요 목록 — 최신순 페이징. */
    @Query(
        value = "SELECT pl FROM PostLike pl WHERE pl.postId = :postId AND pl.deletedAt IS NULL",
        countQuery = "SELECT COUNT(pl) FROM PostLike pl WHERE pl.postId = :postId AND pl.deletedAt IS NULL")
    Page<PostLike> findActiveByPostId(@Param("postId") Long postId, Pageable pageable);

    /** 사용자가 좋아요한 게시글 ID 목록 — 피드 렌더링 시 "내가 좋아요 눌렀는지" 확인용. */
    @Query("SELECT pl.postId FROM PostLike pl WHERE pl.userId = :userId AND pl.postId IN :postIds AND pl.deletedAt IS NULL")
    Set<Long> findLikedPostIdsByUserIdAndPostIdIn(
        @Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
```

## 4. Service 설계

```java
@Service
@RequiredArgsConstructor
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;

    /**
     * 좋아요 — 토글 방식이 아닌 명시적 생성.
     *
     * 흐름:
     * 1. 게시글 존재 확인
     * 2. 기존 좋아요 조회 (soft deleted 포함)
     *    - 활성 좋아요 존재 → duplicateLike (409)
     *    - 취소된 좋아요 존재 → restore (재좋아요)
     *    - 없음 → 새 PostLike 생성
     * 3. Post.likeCount 원자적 증가
     */
    @Transactional
    public PostLike like(Long userId, Long postId) {
        validatePostExists(postId);

        Optional<PostLike> existing = postLikeRepository.findByUserIdAndPostId(userId, postId);

        PostLike postLike;
        if (existing.isPresent()) {
            PostLike found = existing.get();
            if (!found.isCancelled()) {
                throw PostLikeException.duplicateLike();
            }
            // 취소된 좋아요 복원 (재좋아요)
            found.restore(ReactionType.LIKE);
            postLike = found;
        } else {
            postLike = postLikeRepository.save(new PostLike(userId, postId));
        }

        postRepository.incrementLikeCount(postId);
        return postLike;
    }

    /**
     * 좋아요 취소 — soft delete.
     *
     * 흐름:
     * 1. 활성 좋아요 조회
     *    - 없음 → notLiked (400)
     * 2. soft delete (cancel)
     * 3. Post.likeCount 원자적 감소
     */
    @Transactional
    public void unlike(Long userId, Long postId) {
        PostLike postLike = postLikeRepository.findActiveByUserIdAndPostId(userId, postId)
            .orElseThrow(PostLikeException::notLiked);
        postLike.cancel();
        postRepository.decrementLikeCount(postId);
    }

    /** 좋아요 여부 확인. */
    public boolean isLiked(Long userId, Long postId) {
        return postLikeRepository.existsActiveByUserIdAndPostId(userId, postId);
    }

    /** 게시글의 좋아요 목록. */
    public Page<PostLike> getLikes(Long postId, Pageable pageable) {
        return postLikeRepository.findActiveByPostId(postId, pageable);
    }

    /** 피드 렌더링용 — 여러 게시글에 대한 내 좋아요 상태 일괄 조회. */
    public Set<Long> getLikedPostIds(Long userId, Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return Set.of();
        return postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(userId, postIds);
    }

    private void validatePostExists(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw PostException.notFound(postId);
        }
    }
}
```

## 5. 좋아요 생성 흐름 상세

```
[사용자] POST /api/v1/post/42/like
           ↓
[PostLikeService.like(userId=1, postId=42)]
  1. validatePostExists(42) → postRepository.existsById(42)
     → false 면 PostException.notFound(42)
  2. postLikeRepository.findByUserIdAndPostId(1, 42)
     → 케이스 분기:
     ┌─────────────────────────────────────────────────┐
     │ A. 없음 (첫 좋아요)                              │
     │    → new PostLike(1, 42, LIKE) → save → INSERT  │
     ├─────────────────────────────────────────────────┤
     │ B. 존재 + isCancelled=false (이미 좋아요 중)       │
     │    → PostLikeException.duplicateLike() → 409     │
     ├─────────────────────────────────────────────────┤
     │ C. 존재 + isCancelled=true (취소 후 재좋아요)      │
     │    → found.restore(LIKE)                         │
     │    → deletedAt=null, createdAt=now               │
     └─────────────────────────────────────────────────┘
  3. postRepository.incrementLikeCount(42) → likeCount + 1
  4. return postLike → 201 Created
```

## 6. 좋아요 취소 흐름

```
[사용자] DELETE /api/v1/post/42/like
           ↓
[PostLikeService.unlike(userId=1, postId=42)]
  1. findActiveByUserIdAndPostId(1, 42)
     → 없음 → PostLikeException.notLiked() → 400
     → 있음 → postLike
  2. postLike.cancel() → deletedAt = now
  3. postRepository.decrementLikeCount(42) → likeCount - 1
  4. → 204 No Content
```

## 7. Soft Delete + 재좋아요 (Follow 패턴)

Follow 도메인과 동일한 soft delete + restore 패턴 적용.

| 상태 | deletedAt | 의미 |
|------|-----------|------|
| 활성 | null | 좋아요 중 |
| 취소 | not null | 좋아요 취소됨 |
| 재좋아요 | null (restore) | 취소 후 다시 좋아요 → 기존 행 복원 |

> **UNIQUE 제약과 soft delete**: `UNIQUE (user_id, post_id)` 는 soft deleted 행도 포함. 따라서 같은 user+post 조합에 행이 1개만 존재하고, 취소/재좋아요는 같은 행의 상태 변경으로 처리된다.

## 8. 동시성 설계 — 원자적 업데이트

### 8.1 동시성 이슈 시나리오

```
❌ 문제: 애플리케이션 레벨 count++ (lost update)

[스레드 A] SELECT likeCount FROM posts WHERE id=42  → 10
[스레드 B] SELECT likeCount FROM posts WHERE id=42  → 10
[스레드 A] UPDATE posts SET likeCount = 11 WHERE id=42
[스레드 B] UPDATE posts SET likeCount = 11 WHERE id=42  ← A 의 갱신을 덮어씀
→ 결과: 실제 12 인데 11 로 저장 (lost update)
```

```
✅ 해결: DB 원자적 UPDATE (SET count = count + 1)

[스레드 A] UPDATE posts SET like_count = like_count + 1 WHERE id=42  → 11 (DB 레벨 원자적)
[스레드 B] UPDATE posts SET like_count = like_count + 1 WHERE id=42  → 12 (A 반영 후 실행)
→ 결과: 12 — 정확
```

### 8.2 방어 전략 매트릭스

| 시나리오 | 방어 방식 | 구현 |
|---------|----------|------|
| **같은 사용자 동시 2회 좋아요** | `UNIQUE (user_id, post_id)` 제약 → `DataIntegrityViolationException` → 409 변환 | DB 레벨 |
| **다른 사용자 동시 좋아요 (hot post)** | `SET likeCount = likeCount + 1` DB 원자적 UPDATE | `PostRepository.incrementLikeCount()` |
| **좋아요 + 취소 동시 요청** | `findByUserIdAndPostId` → soft delete 상태 확인 → 한 쪽만 성공 | 애플리케이션 + DB |
| **좋아요 취소 시 카운트 음수** | `SET likeCount = likeCount - 1 WHERE likeCount > 0` | DB 레벨 |

### 8.3 FollowCount 패턴과의 일관성

| 항목 | FollowCount | Post.likeCount |
|------|-------------|----------------|
| 저장 위치 | 별도 `follow_counts` 테이블 | `posts` 테이블 임베드 |
| 갱신 방식 | `@Modifying @Query SET count = count + 1` | 동일 |
| 음수 방지 | `WHERE count > 0` | 동일 |
| 데드락 방지 | user_id 오름차순 UPDATE | 해당 없음 (단일 행 UPDATE) |
| 동시성 모델 | DB 원자적 UPDATE | 동일 |

> **설계 원칙**: 프로젝트 전체에서 비정규화 카운트는 **DB 원자적 UPDATE** (`SET count = count + 1`) 로 통일. 애플리케이션 레벨 `findById` + `setCount` + `save` 패턴 금지.

### 8.4 UNIQUE 위반 race 처리

```java
// PostLikeService.like() 에서 UNIQUE 위반 발생 시
try {
    postLike = postLikeRepository.save(new PostLike(userId, postId));
} catch (DataIntegrityViolationException e) {
    // 동시 요청이 이미 좋아요 생성함 → 409 변환
    throw PostLikeException.duplicateLike();
}
```

> Follow 도메인의 `DataIntegrityViolationException` → `alreadyFollowing` 변환 패턴과 동일.

## 9. API 계약

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/post/{id}/like` | 좋아요 | 인증 |
| DELETE | `/api/v1/post/{id}/like` | 좋아요 취소 | 인증 |
| GET | `/api/v1/post/{id}/like/status` | 좋아요 여부 확인 | 인증 |
| GET | `/api/v1/post/{id}/likes` | 좋아요 목록 (페이징) | 공개 |

**POST /api/v1/post/{id}/like — 좋아요**

```json
// Response 201
{
  "id": 1,
  "userId": 1,
  "postId": 42,
  "reaction": "LIKE",
  "createdAt": "2026-04-18T12:00:00Z"
}

// Error 409 — 이미 좋아요한 게시글
{ "message": "이미 좋아요한 게시글입니다." }
```

**GET /api/v1/post/{id}/like/status — 좋아요 여부**

```json
// Response 200
{ "liked": true }
```

## 10. 인덱스 전략

```sql
-- UNIQUE 제약 (자동 인덱스 생성)
UNIQUE (user_id, post_id)

-- 게시글별 좋아요 목록 조회
CREATE INDEX idx_post_likes_post_id ON post_likes (post_id);

-- 사용자별 좋아요 목록 조회
CREATE INDEX idx_post_likes_user_id ON post_likes (user_id);

-- 부분 인덱스 (PostgreSQL) — 활성 좋아요만
CREATE INDEX idx_post_likes_post_active
    ON post_likes (post_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_post_likes_user_active
    ON post_likes (user_id, created_at DESC)
    WHERE deleted_at IS NULL;
```

## 11. DTO 설계

```java
// 좋아요 응답
public record PostLikeResponse(
    Long id, Long userId, Long postId, ReactionType reaction, Instant createdAt
) {
    public static PostLikeResponse from(PostLike postLike) { ... }
}

// 좋아요 여부 응답
public record LikeStatusResponse(boolean liked) {}

// 피드 렌더링용 — 여러 게시글의 좋아요 상태 일괄 확인
// (프론트에서 피드 로딩 후 내가 좋아요 누른 게시글 표시)
public record LikedPostIdsResponse(Set<Long> likedPostIds) {}
```

## 12. 피드 렌더링 최적화 — "내가 좋아요 눌렀는지"

피드에서 각 게시글마다 좋아요 여부를 확인하면 N+1 문제가 발생한다.

```
❌ N+1 패턴
for (Post post : feed) {
    boolean liked = postLikeService.isLiked(userId, post.getId());  // N 쿼리
}

✅ 일괄 조회 패턴
List<Long> postIds = feed.stream().map(Post::getId).toList();
Set<Long> likedIds = postLikeService.getLikedPostIds(userId, postIds);  // 1 쿼리 (IN)
```

> **프론트 연동**: 피드 로딩 후 `GET /api/v1/post/liked?postIds=1,2,3,...` 으로 일괄 조회하거나, PostResponse 에 `liked` 필드를 서버에서 채워서 반환.

## 13. 향후 확장 — 다종 리액션

### 13.1 확장 시나리오

```
[현재] LIKE 1종
  → PostLike.reaction = LIKE (고정)
  → Post.likeCount (단일 카운트)

[향후] LIKE + 6종 Reaction
  → PostLike.reaction = LIKE | CELEBRATE | SUPPORT | ... (enum 확장)
  → Post.reactionCount (총합) 또는 reaction_counts JSONB (종류별 카운트)
```

### 13.2 확장 시 변경 범위

| 레이어 | 변경 | 비용 |
|--------|------|------|
| ReactionType enum | 값 추가 | 없음 (DB 스키마 변경 불필요) |
| PostLike entity | 변경 없음 | — |
| PostLikeService | `like(userId, postId, reactionType)` 파라미터 추가 | 소 |
| Post 카운트 | `likeCount` → `reactionCount` 또는 JSONB | 중 |
| API | `POST /api/v1/post/{id}/reaction` + `type` 파라미터 | 소 |
| 프론트 | 리액션 피커 UI | 중 |

### 13.3 카운트 전략 (향후 결정)

| 방식 | 장점 | 단점 |
|------|------|------|
| `reactionCount` (총합 1개) | 단순, 현재 likeCount 와 동일 | 종류별 카운트 불가 |
| `reaction_counts` JSONB | 종류별 카운트 유연 (`{"LIKE":10,"LOVE":3}`) | PostgreSQL 전용, 원자적 UPDATE 복잡 |
| 별도 `post_reaction_counts` 테이블 | 정규화, 종류별 인덱싱 | FollowCount 패턴과 유사하지만 테이블 추가 |

> **현재 전략**: `Post.likeCount` 단일 카운트 유지. 리액션 도입 시 JSONB 방식 또는 별도 테이블 검토.

## 14. 대량 쓰기 대응 — post_likes 테이블 성능 전략

### 14.1 문제 인식

좋아요는 SNS 에서 **가장 빈번한 쓰기 작업**이다. 인기 게시글은 초당 수천 건의 좋아요가 발생할 수 있으며, 이는 두 가지 병목을 유발한다:

```
1. post_likes 테이블: 대량 INSERT (행 생성) + UNIQUE 인덱스 갱신
2. posts 테이블: 대량 UPDATE (likeCount = likeCount + 1) — hot row 경합
```

### 14.2 단계별 대응 로드맵

| 단계 | 규모 | 전략 | 구현 난이도 |
|------|------|------|-----------|
| **Stage 1 (현재)** | < 만 DAU | DB 직접 쓰기 + 원자적 UPDATE | ✅ 구현 완료 |
| **Stage 2** | 만~10만 DAU | Write-behind 배치 + Redis 버퍼 | 중 |
| **Stage 3** | 10만+ DAU | 이벤트 기반 비동기 처리 (Kafka) | 고 |

### 14.3 Stage 1 — 현재 구현 (DB 직접 쓰기)

```java
// 좋아요 생성 → likeCount 원자적 증가 (단일 트랜잭션)
@Transactional
public PostLike like(Long userId, Long postId) {
    // ... 검증 + PostLike INSERT ...
    postRepository.incrementLikeCount(postId);  // SET likeCount = likeCount + 1
    return postLike;
}
```

**장점**: 단순, 정확, 트랜잭션 보장
**한계**: hot post 에서 posts 행 잠금 경합 (초당 수백 UPDATE 동일 행)

### 14.4 Stage 2 — Redis Write-behind 버퍼 (향후)

```
[사용자 좋아요 클릭]
    ↓
[PostLikeService.like()]
    ├── PostLike INSERT → post_likes 테이블 (즉시)
    └── Redis INCR post:{id}:like_delta (즉시, DB UPDATE 대신)

[Scheduler (30초~1분 주기)]
    ├── Redis GETDEL post:*:like_delta
    └── UPDATE posts SET like_count = like_count + {delta} WHERE id = ?
        → 30초간 누적된 delta 를 한 번에 반영
```

| 항목 | 현재 (Stage 1) | Stage 2 |
|------|---------------|---------|
| posts UPDATE 빈도 | 좋아요 1건당 1회 | 30초당 1회 (배치) |
| 초당 100 좋아요 시 | 100 UPDATE/초 | ~2 UPDATE/분 |
| 정확도 | 실시간 정확 | 최대 30초 지연 (SNS 허용 범위) |
| 복잡성 | 낮음 | Redis 의존 추가 |

### 14.5 Stage 3 — 이벤트 기반 비동기 (향후)

```
[좋아요 API]
    ├── PostLike INSERT (동기)
    └── Kafka PRODUCE "like.created" event (비동기)

[Like Count Consumer]
    ├── 이벤트 수집 (버퍼링)
    └── 주기적 batch UPDATE posts SET like_count = ...
```

### 14.6 post_likes 테이블 자체의 쓰기 최적화

| 기법 | 설명 |
|------|------|
| **UNIQUE 인덱스 부담 감소** | 부분 인덱스 (`WHERE deleted_at IS NULL`) 사용 시 soft deleted 행 제외 → 인덱스 크기 축소 |
| **파티셔닝 (향후)** | `post_id` range 또는 hash 파티셔닝 → hot partition 분산 |
| **Batch INSERT** | 클라이언트 요청을 짧은 시간 내 모아서 batch insert (Kafka Consumer 패턴) |
| **Connection Pool 튜닝** | `HikariCP.maximum-pool-size` 확대 + `statement-cache-size` 설정 |

### 14.7 모니터링 지표

| 지표 | 임계값 | 조치 |
|------|--------|------|
| `post_likes` INSERT 지연 | > 100ms | 인덱스 점검, 커넥션 풀 확대 |
| `posts` UPDATE 지연 (likeCount) | > 50ms | Stage 2 Redis 버퍼 도입 검토 |
| DB CPU 사용률 | > 70% | 읽기 복제본 분리, 쓰기 최적화 |
| 데드락 발생 | > 0 | 잠금 순서 검토 (like 는 단일 행이므로 발생 가능성 낮음) |

## 15. Flyway 마이그레이션 (V5)

```sql
-- V5: 좋아요 테이블 생성
CREATE TABLE post_likes (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    post_id     BIGINT      NOT NULL,
    reaction    VARCHAR(20) NOT NULL DEFAULT 'LIKE',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at  TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uq_post_likes_user_post UNIQUE (user_id, post_id)
);

CREATE INDEX idx_post_likes_post_id ON post_likes (post_id);
CREATE INDEX idx_post_likes_user_id ON post_likes (user_id);

-- 부분 인덱스 (PostgreSQL)
CREATE INDEX idx_post_likes_post_active
    ON post_likes (post_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_post_likes_user_active
    ON post_likes (user_id, created_at DESC) WHERE deleted_at IS NULL;
```
