# Post Service Design — 게시글 도메인 설계

> 이 문서는 게시글(Post) 도메인의 엔티티, 비즈니스 규칙, API 계약, 인덱스 전략을 정의한다.
> 대규모 SNS(Twitter/X, Threads, Reddit, Instagram)를 참고하여 설계.

## 1. 게시글 유형

### 1.1 유형 분류

```
┌─────────────────────────────────────────────────────────┐
│                    게시글 (Post)                         │
├──────────┬──────────┬──────────┬────────────────────────┤
│ ORIGINAL │  REPLY   │  QUOTE   │       REPOST           │
│ 일반 글  │  답글    │  인용    │       리포스트          │
├──────────┼──────────┼──────────┼────────────────────────┤
│ parent ✕ │ parent ✓ │ quote ✓  │ repost ✓               │
│ quote  ✕ │ quote  ✕ │ parent ? │ content = null         │
│ repost ✕ │ repost ✕ │ repost ✕ │ 원본 그대로 공유       │
│ content✓ │ content✓ │ content✓ │ (자체 콘텐츠 없음)      │
└──────────┴──────────┴──────────┴────────────────────────┘
```

| 유형 | parentId | quoteId | repostId | content | 설명 |
|------|----------|---------|----------|---------|------|
| **ORIGINAL** | null | null | null | 필수 | 독립적인 새 게시글 |
| **REPLY** | 부모 ID | null | null | 필수 | 특정 게시글에 대한 답글. 스레드 형성 |
| **QUOTE** | null 또는 부모 ID | 인용 ID | null | 필수 | 다른 게시글을 인용 + 본인 의견 |
| **REPOST** | null | null | 원본 ID | null | 원본 그대로 공유 (Twitter Retweet) |

### 1.2 유형 유도 (PostType enum)

DB 에 별도 컬럼으로 저장하지 않고 참조 ID 조합에서 유도한다.

```java
public enum PostType {
    ORIGINAL, REPLY, QUOTE, REPOST;

    public static PostType of(Post post) {
        if (post.getRepostId() != null) return REPOST;
        if (post.getQuoteId() != null) return QUOTE;
        if (post.getParentId() != null) return REPLY;
        return ORIGINAL;
    }
}
```

> **DB 컬럼 미저장 이유**: parentId/quoteId/repostId 조합에서 항상 유도 가능. 별도 컬럼은 불일치 위험만 추가.

### 1.3 불변식 (비즈니스 규칙)

| 규칙 | 설명 |
|------|------|
| `author` not null | 모든 게시글에 작성자 필수 |
| 리포스트가 아니면 `content` 필수 | ORIGINAL/REPLY/QUOTE 는 본문 not blank + 500자 이하 |
| 리포스트면 `content` null | 자체 콘텐츠 없이 원본만 공유 |
| 리포스트는 단독 행위 | `repostId != null` 이면 `parentId == null && quoteId == null` |
| 자기 참조 금지 | parentId/quoteId/repostId 가 자신의 id 와 같을 수 없음 |
| 답글+인용 동시 가능 | parentId + quoteId 동시 설정 허용 (Twitter 모델) |

## 2. Entity 설계

### 2.1 필드 정의

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, IDENTITY | — |
| author | User | `@ManyToOne(LAZY)`, FK 제약 없음 | 작성자 |
| content | String | nullable, max 500자 | 게시글 본문. 리포스트/삭제 시 null |
| parentId | Long | nullable | 답글 대상 게시글 ID |
| quoteId | Long | nullable | 인용 대상 게시글 ID |
| repostId | Long | nullable | 리포스트 원본 게시글 ID |
| replyCount | long | not null, default 0 | 답글 수 (비정규화) |
| repostCount | long | not null, default 0 | 리포스트 수 (비정규화) |
| likeCount | long | not null, default 0 | 좋아요 수 (비정규화) |
| viewCount | long | not null, default 0 | 조회 수 (비정규화) |
| shareCount | long | not null, default 0 | 외부 공유 수 (비정규화) |
| createdAt | Instant | not null (BaseEntity) | 생성 시각 |
| updatedAt | Instant | not null (BaseEntity) | 수정 시각 |
| deletedAt | Instant | nullable (BaseEntity) | 소프트 삭제 시각 |

### 2.2 참조 ID 를 `Long` 으로 선택한 이유

| 방식 | 장점 | 단점 |
|------|------|------|
| `@ManyToOne Post parent` | JPA 연관관계 자동 관리 | N+1, 순환 참조, 삭제된 게시글 참조 시 예외 |
| **`Long parentId`** | 피드 시 추가 JOIN 없음, 삭제된 게시글도 ID 유지, FK 제약 없음 정책과 일관 | 참조 무결성 애플리케이션 책임 |

> **선택**: `Long` — SNS 피드는 수만 건을 스캔. 불필요한 JOIN 은 성능에 직접 영향. 참조된 게시글은 프론트에서 별도 API 로 lazy 로딩.

### 2.3 Entity 코드

```java
@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_posts_author_id", columnList = "author_id"),
    @Index(name = "idx_posts_author_created", columnList = "author_id, created_at DESC"),
    @Index(name = "idx_posts_parent_id", columnList = "parent_id"),
    @Index(name = "idx_posts_parent_created", columnList = "parent_id, created_at DESC")
})
@Getter
public class Post extends BaseEntity {

    public static final int MAX_CONTENT_LENGTH = 500;
    public static final int EDIT_WINDOW_MINUTES = 20;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false,
        foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private User author;

    @Column(length = MAX_CONTENT_LENGTH)
    private String content;  // nullable: 리포스트 또는 삭제 시 null

    @Column
    private Long parentId;   // 답글 대상

    @Column
    private Long quoteId;    // 인용 대상

    @Column
    private Long repostId;   // 리포스트 원본

    // 비정규화 카운트 — DB 원자적 UPDATE 로 갱신
    @Column(nullable = false)
    private long replyCount;
    @Column(nullable = false)
    private long repostCount;
    @Column(nullable = false)
    private long likeCount;
    @Column(nullable = false)
    private long viewCount;
    @Column(nullable = false)
    private long shareCount;

    protected Post() {}

    // --- 정적 팩토리 메서드 (유형별 생성) ---

    /** 일반 게시글 */
    public Post(User author, String content) {
        validateAuthor(author);
        validateContent(content);
        this.author = author;
        this.content = content;
    }

    /** 답글 */
    public static Post reply(User author, String content, Long parentId) {
        if (parentId == null) throw PostException.invalidField("parentId");
        Post post = new Post(author, content);
        post.parentId = parentId;
        return post;
    }

    /** 인용 */
    public static Post quote(User author, String content, Long quoteId) {
        if (quoteId == null) throw PostException.invalidField("quoteId");
        Post post = new Post(author, content);
        post.quoteId = quoteId;
        return post;
    }

    /** 리포스트 — content 없음 */
    public static Post repost(User author, Long repostId) {
        validateAuthor(author);
        if (repostId == null) throw PostException.invalidField("repostId");
        Post post = new Post();
        post.author = author;
        post.repostId = repostId;
        // content = null (리포스트는 자체 콘텐츠 없음)
        return post;
    }

    // --- 도메인 메서드 ---

    /** 게시글 유형 유도 */
    public PostType getType() { return PostType.of(this); }

    /** 수정 가능 여부 — 20분 윈도우 + 리포스트 제외 */
    public boolean isEditable() {
        return repostId == null
            && createdAt != null
            && Duration.between(createdAt, Instant.now()).toMinutes() < EDIT_WINDOW_MINUTES;
    }

    /** 콘텐츠 수정 — 20분 내, 리포스트 아닌 경우만 */
    public void updateContent(String content) {
        if (!isEditable()) throw PostException.editWindowExpired();
        validateContent(content);
        this.content = content;
    }

    /** 소프트 삭제 — 콘텐츠 제거 + deletedAt 설정 */
    public void softDelete() {
        this.content = null;
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() { return deletedAt != null; }

    public boolean isAuthor(Long userId) {
        return author != null && author.getId() != null && author.getId().equals(userId);
    }

    private static void validateAuthor(User author) {
        if (author == null) throw PostException.invalidField("author");
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) throw PostException.invalidField("content");
        if (content.length() > MAX_CONTENT_LENGTH) throw PostException.contentTooLong();
    }
}
```

## 3. Repository 설계

```java
public interface PostRepository extends JpaRepository<Post, Long> {

    /** 단건 조회 — author fetch join */
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Post> findWithAuthorById(Long id);

    /** 전체 피드 — 삭제되지 않은 게시글만, 최신순 */
    @Query(
        value = "SELECT p FROM Post p JOIN FETCH p.author WHERE p.deletedAt IS NULL",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deletedAt IS NULL")
    Page<Post> findAllWithAuthor(Pageable pageable);

    /** 사용자의 게시글 — 프로필 피드 */
    @Query(
        value = "SELECT p FROM Post p JOIN FETCH p.author WHERE p.author.id = :authorId AND p.deletedAt IS NULL",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.author.id = :authorId AND p.deletedAt IS NULL")
    Page<Post> findByAuthorIdWithAuthor(@Param("authorId") Long authorId, Pageable pageable);

    /** 답글 목록 (스레드) — 특정 게시글의 답글들 */
    @Query(
        value = "SELECT p FROM Post p JOIN FETCH p.author WHERE p.parentId = :parentId AND p.deletedAt IS NULL",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.parentId = :parentId AND p.deletedAt IS NULL")
    Page<Post> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);

    /** 인용 목록 — 특정 게시글을 인용한 게시글들 */
    @Query(
        value = "SELECT p FROM Post p JOIN FETCH p.author WHERE p.quoteId = :quoteId AND p.deletedAt IS NULL",
        countQuery = "SELECT COUNT(p) FROM Post p WHERE p.quoteId = :quoteId AND p.deletedAt IS NULL")
    Page<Post> findQuotesByQuoteId(@Param("quoteId") Long quoteId, Pageable pageable);

    /** 중복 인용 확인 — 같은 사용자가 같은 게시글을 2회 이상 인용했는지 */
    boolean existsByQuoteIdAndAuthorId(Long quoteId, Long authorId);

    /** 사용자 삭제 시 해당 사용자의 모든 게시글 물리 삭제 */
    @Modifying
    @Query("DELETE FROM Post p WHERE p.author = :author")
    void deleteAllByAuthor(@Param("author") User author);

    // --- 비정규화 카운트 원자적 갱신 ---

    @Modifying
    @Query("UPDATE Post p SET p.replyCount = p.replyCount + 1 WHERE p.id = :postId")
    int incrementReplyCount(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.replyCount = p.replyCount - 1 WHERE p.id = :postId AND p.replyCount > 0")
    int decrementReplyCount(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.repostCount = p.repostCount + 1 WHERE p.id = :postId")
    int incrementRepostCount(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.repostCount = p.repostCount - 1 WHERE p.id = :postId AND p.repostCount > 0")
    int decrementRepostCount(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    int incrementLikeCount(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
    int decrementLikeCount(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :postId")
    int incrementViewCount(@Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.shareCount = p.shareCount + 1 WHERE p.id = :postId")
    int incrementShareCount(@Param("postId") Long postId);
}
```

## 4. Service 설계

```java
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /** 게시글 생성 — 유형에 따라 분기 */
    @Transactional
    public Post create(Long authorId, String content, Long parentId, Long quoteId, Long repostId) {
        User author = userRepository.findById(authorId)
            .orElseThrow(() -> PostException.authorNotFound(authorId));

        Post post;
        if (repostId != null) {
            // 리포스트: content 금지, parentId/quoteId 금지
            validateRepostTarget(repostId);
            post = Post.repost(author, repostId);
            postRepository.save(post);
            postRepository.incrementRepostCount(repostId);
        } else if (quoteId != null) {
            validateQuoteTarget(quoteId, authorId);
            post = Post.quote(author, content, quoteId);
            if (parentId != null) post.setParentId(parentId); // 답글+인용
            postRepository.save(post);
            // 인용도 리포스트에 포함 — 원본의 repostCount 증가
            postRepository.incrementRepostCount(quoteId);
        } else if (parentId != null) {
            validateReplyTarget(parentId);
            post = Post.reply(author, content, parentId);
            postRepository.save(post);
            postRepository.incrementReplyCount(parentId);
        } else {
            post = new Post(author, content);
            postRepository.save(post);
        }

        return post;
    }

    /**
     * 수정 — 20분 윈도우.
     *
     * 답글(parentId != null)인 경우에도 동일한 수정 규칙 적용.
     * parentId 자체는 변경 불가 (답글 대상 이동 금지).
     * 리포스트는 content 가 없으므로 수정 대상 자체가 아님 (isEditable 에서 차단).
     */
    @Transactional
    public Post update(Long requesterId, Long id, String content) {
        Post post = getById(id);
        if (!post.isAuthor(requesterId)) throw PostException.forbidden(id);
        post.updateContent(content);  // isEditable 내부 검증 (20분 + 리포스트 제외)
        return post;
    }

    /**
     * 삭제 — soft delete.
     *
     * 답글(parentId != null) 삭제 시 부모 게시글의 replyCount 를 감소시킨다.
     * 리포스트(repostId != null) 삭제 시 원본 게시글의 repostCount 를 감소시킨다.
     * 일반 게시글/인용 삭제 시에는 부모 카운트 감소 없음.
     */
    @Transactional
    public void delete(Long requesterId, Long id) {
        Post post = getById(id);
        if (!post.isAuthor(requesterId)) throw PostException.forbidden(id);
        post.softDelete();
        // 답글인 경우 — 부모 게시글의 답글 수 감소
        if (post.getParentId() != null) {
            postRepository.decrementReplyCount(post.getParentId());
        }
        // 리포스트인 경우 — 원본 게시글의 리포스트 수 감소
        if (post.getRepostId() != null) {
            postRepository.decrementRepostCount(post.getRepostId());
        }
        // 인용인 경우 — 인용된 원본 게시글의 리포스트 수 감소 (인용도 리포스트에 포함)
        if (post.getQuoteId() != null) {
            postRepository.decrementRepostCount(post.getQuoteId());
        }
    }

    /**
     * 답글 대상 검증 — 부모 게시글이 존재하고 삭제되지 않았는지 확인.
     * 삭제된 게시글에도 답글을 허용할지는 정책 결정 사항.
     * 현재: 삭제된 게시글에는 답글 금지 (스레드 확장 방지).
     */
    private void validateReplyTarget(Long parentId) {
        Post parent = postRepository.findWithAuthorById(parentId)
            .orElseThrow(() -> PostException.notFound(parentId));
        if (parent.isDeleted()) {
            throw PostException.replyToDeletedPost();
        }
    }

    /**
     * 인용 대상 검증 — 인용 게시글 존재 확인 + 본인 중복 인용 확인.
     * 인용은 "임베드가 포함된 독립 게시글"이므로 삭제된 원본도 인용 가능 (링크는 "[삭제됨]" 표시).
     * 단, 같은 사용자가 같은 게시글을 2회 이상 인용하는 것은 금지 (스팸 방지).
     */
    private void validateQuoteTarget(Long quoteId, Long authorId) {
        if (!postRepository.existsById(quoteId)) {
            throw PostException.notFound(quoteId);
        }
        if (postRepository.existsByQuoteIdAndAuthorId(quoteId, authorId)) {
            throw PostException.duplicateQuote();
        }
    }

    private void validateRepostTarget(Long repostId) {
        Post original = postRepository.findWithAuthorById(repostId)
            .orElseThrow(() -> PostException.notFound(repostId));
        if (original.isDeleted()) {
            throw PostException.repostDeletedPost();
        }
        // 리포스트의 리포스트 금지 (원본만 리포스트 가능)
        if (original.getRepostId() != null) {
            throw PostException.repostOfRepost();
        }
    }

    // ... getById, getFeed, getReplies, getQuotes, getUserPosts
}
```

### 4.1 답글 생성 흐름

```
[사용자] POST /api/v1/post { "content": "저도요!", "parentId": 42 }
           ↓
[PostService.create]
  1. User 존재 확인 (authorId → User)
  2. parentId != null → validateReplyTarget(42)
     → postRepository.findWithAuthorById(42)
     → 부모 존재 확인 + 삭제 여부 확인
  3. Post.reply(author, "저도요!", 42) → Post 생성
     → parentId = 42, content = "저도요!", type = REPLY
  4. postRepository.save(post) → INSERT
  5. postRepository.incrementReplyCount(42) → 부모 replyCount + 1
  6. return post → 201 Created
```

### 4.2 답글 수정/삭제 시 검증

| 작업 | 검증 | 카운트 변동 |
|------|------|------------|
| 답글 수정 | `isAuthor` + `isEditable` (20분 윈도우) | 없음 (parentId 변경 불가) |
| 답글 삭제 | `isAuthor` → `softDelete()` → `parentId != null` 이면 부모 `replyCount - 1` | replyCount - 1 |
| 일반 글 삭제 | `isAuthor` → `softDelete()` → `parentId == null` 이면 카운트 변동 없음 | 없음 |

> **parentId 변경 불가**: 답글을 다른 게시글로 이동하는 것은 허용하지 않는다. 수정은 content 만 대상.

### 4.3 인용 생성 흐름

인용(Quote)은 **임베드가 포함된 독립적인 게시글**이다. 원본 게시글이 카드 형태로 임베디드되고, 인용자의 본인 의견이 함께 표시된다.

```
[사용자] POST /api/v1/post { "content": "이 글 정말 공감합니다", "quoteId": 42 }
           ↓
[PostService.create]
  1. User 존재 확인 (authorId → User)
  2. quoteId != null → validateQuoteTarget(42, authorId)
     → postRepository.existsById(42) → 인용 대상 존재 확인
     → postRepository.existsByQuoteIdAndAuthorId(42, authorId) → 중복 인용 확인
  3. Post.quote(author, "이 글 정말 공감합니다", 42) → Post 생성
     → quoteId = 42, content = "이 글 정말 공감합니다", type = QUOTE
  4. postRepository.save(post) → INSERT
  5. postRepository.incrementRepostCount(42) → 원본 repostCount + 1 (인용도 리포스트에 포함)
  6. return post → 201 Created
```

**인용 규칙**:

| 규칙 | 설명 | 참고 |
|------|------|------|
| 인용 = 독립 게시글 | 인용글은 자체 content 필수. 원본은 임베드로 표시 | Twitter Quote Tweet |
| 인용도 리포스트 카운트에 포함 | 원본의 `repostCount` 를 증가 — 인용+리포스트 합산 | Twitter: "Repost" 버튼에 Quote 포함 |
| 중복 인용 금지 | 같은 사용자가 같은 게시글을 2회 이상 인용 불가 (스팸 방지) | — |
| 삭제된 원본도 인용 가능 | 인용은 독립 게시글이므로 원본 삭제와 무관. 프론트에서 "[삭제된 게시글]" 표시 | Twitter 동작 |
| 인용 삭제 시 원본 repostCount 감소 | 인용글 soft delete → 원본 `decrementRepostCount` | — |
| 인용의 인용 가능 | 인용글 자체도 다시 인용 가능 (재귀) | Twitter 허용 |

### 4.4 인용 수정/삭제

| 작업 | 검증 | 카운트 변동 |
|------|------|------------|
| 인용 수정 | `isAuthor` + `isEditable` (20분) | 없음 (quoteId 변경 불가, content 만 수정) |
| 인용 삭제 | `isAuthor` → `softDelete()` → `quoteId != null` → 원본 `repostCount - 1` | repostCount - 1 |

> **quoteId 변경 불가**: 인용 대상을 다른 게시글로 변경하는 것은 허용하지 않는다. 수정은 content 만 대상.

## 5. 수정 정책 상세

### 5.1 왜 20분인가

| 시간 | 근거 |
|------|------|
| **0~5분** | 오타 발견 대부분 이 시간 내 (Threads 5분) |
| **5~20분** | 첫 반응(좋아요/답글) 확인 후 미세 수정 여유 |
| **20분~** | 바이럴 시작 구간. 수정 허용 시 위험 |

### 5.2 수정 윈도우 만료 후 시나리오

```
[사용자] PUT /api/v1/post/42 { "content": "광고 내용으로 변경" }
                ↓
[PostService.update]
  → post.updateContent(content)
    → isEditable() == false (21분 경과)
    → PostException.editWindowExpired()
                ↓
[GlobalExceptionHandler]
  → 400 { "message": "게시글 수정은 작성 후 20분 이내에만 가능합니다." }
```

### 5.3 수정 가능 여부 판정

| 조건 | isEditable | 사유 |
|------|-----------|------|
| 일반 게시글, 10분 경과 | ✅ true | 20분 이내 |
| 답글, 19분 경과 | ✅ true | 20분 이내 |
| 인용, 20분 경과 | ❌ false | 윈도우 만료 |
| 리포스트 | ❌ false | 자체 콘텐츠 없음 |
| 삭제된 게시글 | ❌ false | 이미 삭제됨 |

## 6. Soft Delete 정책

### 6.1 삭제 동작

```java
post.softDelete();
// → this.content = null;     // 콘텐츠 제거 (개인정보/저작권)
// → this.deletedAt = now;    // 삭제 시각 기록
// → 행 자체는 유지           // 답글 스레드 구조 보존
```

### 6.2 삭제된 게시글 표시 (프론트)

```json
// 삭제된 게시글 응답
{
  "id": 42,
  "content": null,
  "author": { "id": 1, "nickname": "alice" },
  "type": "ORIGINAL",
  "deleted": true,
  "replyCount": 5,  // 답글은 살아있음
  ...
}
```

> **Twitter/X**: "이 트윗은 삭제되었습니다" — 스레드에서 답글은 유지, 원본만 가려짐.
> **Reddit**: "[deleted]" — 댓글 트리 구조 유지.

### 6.3 삭제와 카운트

| 삭제 대상 | 부모 카운트 감소 | 사유 |
|----------|----------------|------|
| 답글 삭제 | 부모 replyCount - 1 | 답글 수 정확성 |
| 리포스트 삭제 | 원본 repostCount - 1 | 리포스트 수 정확성 |
| 일반/인용 삭제 | 없음 | 부모 없음 |

## 7. 비정규화 카운트 전략

### 7.1 임베드 vs 별도 테이블

| 방식 | 장점 | 단점 | 채택 |
|------|------|------|------|
| **Post 엔티티 임베드** | 피드 시 JOIN 없이 카운트 포함, 단일 행 완결 | hot post UPDATE 경합 | ✅ |
| 별도 PostEngagement 테이블 | UPDATE 격리, 캐시 독립 | JOIN 비용, 복잡성 | ❌ |

> **FollowCount 와의 차이**: FollowCount 는 사용자당 1행(빈도 낮음). Post 카운트는 게시글당 1행이고 인기 게시글은 초당 수천 UPDATE 가능. 현재 규모에서는 임베드가 적합하지만, 대규모 확장 시 별도 테이블 + Redis 버퍼 검토.

### 7.2 DB 원자적 UPDATE

```java
@Modifying
@Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
int incrementLikeCount(@Param("postId") Long postId);
```

- `SET count = count + 1` → lost update 원천 차단
- `WHERE p.likeCount > 0` (decrement 시) → 음수 방지
- 반환 `int` (affected rows) → 0 이면 게시글 미존재

### 7.3 조회수 갱신 (향후 최적화)

| 현재 | 향후 |
|------|------|
| 매 조회마다 `incrementViewCount` | Redis `INCR post:{id}:views` → 1분마다 batch flush |
| 단순하지만 hot post 에서 DB 부하 | DB UPDATE 횟수 60배 감소 |

## 8. 인덱스 전략

### 8.1 인덱스 목록

| 인덱스 | 컬럼 | 타입 | 커버 쿼리 |
|--------|------|------|----------|
| PK | `id` | 자동 | 단건 조회 |
| idx_posts_author_id | `author_id` | INDEX | 사용자 게시글 목록 |
| idx_posts_author_created | `(author_id, created_at DESC)` | INDEX | 프로필 피드 페이징 |
| idx_posts_parent_id | `parent_id` | PARTIAL | 답글 스레드 조회 |
| idx_posts_parent_created | `(parent_id, created_at DESC)` | PARTIAL | 답글 페이징 |
| idx_posts_quote_id | `quote_id` | PARTIAL | 인용 목록 |
| idx_posts_repost_id | `repost_id` | PARTIAL | 리포스트 목록 |

### 8.2 부분 인덱스 (PostgreSQL)

```sql
-- V4 마이그레이션
CREATE INDEX idx_posts_parent_id ON posts (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_posts_parent_created ON posts (parent_id, created_at DESC) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_posts_quote_id ON posts (quote_id) WHERE quote_id IS NOT NULL;
CREATE INDEX idx_posts_repost_id ON posts (repost_id) WHERE repost_id IS NOT NULL;
```

> **부분 인덱스 근거**: 대부분의 게시글은 ORIGINAL (parentId/quoteId/repostId 모두 null). null 행을 인덱스에서 제외하면 인덱스 크기 대폭 감소.

## 9. API 계약

### 9.1 엔드포인트

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/post` | 게시글 작성 (일반/답글/인용/리포스트) | 인증 |
| GET | `/api/v1/post/{id}` | 단건 조회 | 공개 |
| GET | `/api/v1/post` | 피드 (전체 최신순) | 공개 |
| PUT | `/api/v1/post/{id}` | 수정 (20분 내) | 인증 (작성자) |
| DELETE | `/api/v1/post/{id}` | 삭제 (soft delete) | 인증 (작성자) |
| GET | `/api/v1/post/{id}/replies` | 답글 목록 | 공개 |
| GET | `/api/v1/post/{id}/quotes` | 인용 목록 | 공개 |
| GET | `/api/v1/user/{id}/posts` | 사용자 게시글 | 공개 |

### 9.2 요청/응답

**POST /api/v1/post — 통합 생성**

```json
// 일반 게시글
{ "content": "오늘 날씨가 좋네요" }

// 답글
{ "content": "저도요!", "parentId": 42 }

// 인용
{ "content": "이 글 정말 공감합니다", "quoteId": 42 }

// 리포스트 (content 없음)
{ "repostId": 42 }
```

**Response 201**

```json
{
  "id": 100,
  "content": "오늘 날씨가 좋네요",
  "author": { "id": 1, "nickname": "alice" },
  "type": "ORIGINAL",
  "parentId": null,
  "quoteId": null,
  "repostId": null,
  "replyCount": 0,
  "repostCount": 0,
  "likeCount": 0,
  "viewCount": 0,
  "shareCount": 0,
  "editable": true,
  "deleted": false,
  "createdAt": "2026-04-18T06:00:00Z",
  "updatedAt": "2026-04-18T06:00:00Z"
}
```

**PUT /api/v1/post/{id} — 수정**

```json
// Error 400 — 수정 윈도우 만료
{ "message": "게시글 수정은 작성 후 20분 이내에만 가능합니다." }
```

### 9.3 DTO 설계

```java
public record PostCreateRequest(
    @Size(max = 500) String content,  // 리포스트 시 null 허용
    Long parentId,
    Long quoteId,
    Long repostId
) {}

public record PostUpdateRequest(
    @NotBlank @Size(max = 500) String content
) {}

public record PostResponse(
    Long id,
    String content,
    UserSummaryResponse author,
    PostType type,
    Long parentId,
    Long quoteId,
    Long repostId,
    PostResponse quotedPost,  // 인용된 원본 (1단계만)
    long replyCount,
    long repostCount,
    long likeCount,
    long viewCount,
    long shareCount,
    boolean editable,
    boolean deleted,
    Instant createdAt,
    Instant updatedAt
) {}
```

## 10. 대규모 SNS 비교

| 기능 | Twitter/X | Threads | Reddit | **이 프로젝트** |
|------|-----------|---------|--------|-----------------|
| 일반 게시글 | ✅ Tweet | ✅ Post | ✅ Post | ✅ ORIGINAL |
| 답글 | ✅ in_reply_to | ✅ parent | ✅ parent_id | ✅ parentId |
| 인용 | ✅ Quote Tweet | ❌ | ❌ | ✅ quoteId |
| 리포스트 | ✅ Retweet | ✅ Repost | ✅ Crosspost | ✅ repostId |
| 수정 | 30분 (Blue) | 5분 | 무제한 | **20분** |
| 삭제 | 즉시 (hard) | 즉시 | soft ([deleted]) | **soft delete** |
| 좋아요 | ✅ | ✅ | ✅ upvote | ✅ likeCount |
| 조회 수 | ✅ (2022~) | ❌ | ✅ | ✅ viewCount |
| 수정 이력 | ✅ | ❌ | ✅ (edited) | ⬜ 향후 |

## 11. 향후 확장

- [ ] 좋아요 (PostLike) — `post_likes(id, post_id, user_id, created_at)` + UNIQUE
- [ ] 수정 이력 (PostEditHistory) — 수정 전 원본 content + 수정 시각
- [ ] 북마크 (PostBookmark) — 비공개 저장
- [ ] 해시태그/멘션 파싱 — `#tag`, `@user` 추출 → 별도 인덱스
- [ ] 미디어 첨부 (JSONB metadata) — 이미지/동영상 URL
- [ ] 조회수 Redis 버퍼 — `INCR post:{id}:views` → batch flush
- [ ] 팔로잉 피드 — 내가 팔로우한 사용자의 게시글만 (fan-out-on-read vs fan-out-on-write)
