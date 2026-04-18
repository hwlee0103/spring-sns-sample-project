package com.lecture.spring_sns_sample_project.domain.post;

import com.lecture.spring_sns_sample_project.domain.common.BaseEntity;
import com.lecture.spring_sns_sample_project.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;

/**
 * 게시글 도메인 Entity — ORIGINAL / REPLY / QUOTE / REPOST 4종 통합.
 *
 * <p>게시글 유형은 parentId/quoteId/repostId 조합에서 유도한다 ({@link PostType#of(Post)}). 별도 type 컬럼은 두지 않는다.
 *
 * <p>{@link BaseEntity} 로부터 createdAt, updatedAt, deletedAt 을 상속받는다.
 */
@Entity
@Table(
    name = "posts",
    indexes = {
      @Index(name = "idx_posts_author_id", columnList = "author_id"),
      @Index(name = "idx_posts_author_created", columnList = "author_id, created_at DESC"),
      @Index(name = "idx_posts_parent_id", columnList = "parent_id"),
      @Index(name = "idx_posts_parent_created", columnList = "parent_id, created_at DESC")
    })
@Getter
public class Post extends BaseEntity {

  /** 게시글 최대 길이. 추후 용량/효율성에 따라 증감 가능. */
  public static final int MAX_CONTENT_LENGTH = 1000;

  /** 수정 가능 시간 (분). 이 시간 이후에는 수정 불가. */
  public static final int EDIT_WINDOW_MINUTES = 20;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "author_id",
      nullable = false,
      foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
  private User author;

  /** 게시글 본문. 리포스트 시 null, 소프트 삭제 시 null 처리. */
  @Column(length = MAX_CONTENT_LENGTH)
  private String content;

  /** 답글 대상 게시글 ID. null 이면 최상위 게시글. */
  @Column private Long parentId;

  /** 인용 대상 게시글 ID. null 이면 인용 아님. */
  @Column private Long quoteId;

  /** 리포스트 원본 게시글 ID. null 이면 리포스트 아님. */
  @Column private Long repostId;

  /** 첨부 미디어 ID — Media 도메인 추가 시 연결 (향후). */
  @Column private Long mediaId;

  // 비정규화 카운트 — PostRepository 의 DB 원자적 UPDATE 로 갱신
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

  /** 일반 게시글 생성. */
  public Post(User author, String content) {
    validateAuthor(author);
    validateContent(content);
    this.author = author;
    this.content = content;
  }

  /** 답글 생성. parentId 는 not null. */
  public static Post reply(User author, String content, Long parentId) {
    if (parentId == null) {
      throw PostException.invalidField("parentId");
    }
    Post post = new Post(author, content);
    post.parentId = parentId;
    return post;
  }

  /** 인용 생성. quoteId 는 not null, content 필수. */
  public static Post quote(User author, String content, Long quoteId) {
    if (quoteId == null) {
      throw PostException.invalidField("quoteId");
    }
    Post post = new Post(author, content);
    post.quoteId = quoteId;
    return post;
  }

  /** 답글 + 인용 동시 생성. */
  public static Post replyWithQuote(User author, String content, Long parentId, Long quoteId) {
    Post post = reply(author, content, parentId);
    if (quoteId == null) {
      throw PostException.invalidField("quoteId");
    }
    post.quoteId = quoteId;
    return post;
  }

  /** 리포스트 생성. content 없음 (원본 참조만). */
  public static Post repost(User author, Long repostId) {
    validateAuthor(author);
    if (repostId == null) {
      throw PostException.invalidField("repostId");
    }
    Post post = new Post();
    post.author = author;
    post.repostId = repostId;
    return post;
  }

  // --- 도메인 메서드 ---

  /** 게시글 유형 유도. */
  public PostType getType() {
    return PostType.of(this);
  }

  /** 수정 가능 여부 — 리포스트 제외 + 20분 윈도우 + 미삭제. */
  public boolean isEditable() {
    return repostId == null
        && deletedAt == null
        && createdAt != null
        && Duration.between(createdAt, Instant.now()).toMinutes() < EDIT_WINDOW_MINUTES;
  }

  /** 콘텐츠 수정 — 20분 내, 리포스트/삭제 아닌 경우만. */
  public void updateContent(String content) {
    if (!isEditable()) {
      throw PostException.editWindowExpired();
    }
    validateContent(content);
    this.content = content;
  }

  /** 소프트 삭제 — 콘텐츠 제거 + deletedAt 설정. 스레드 구조는 유지. */
  public void softDelete() {
    this.content = null;
    this.deletedAt = Instant.now();
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public boolean isAuthor(Long userId) {
    return author != null && author.getId() != null && author.getId().equals(userId);
  }

  private static void validateAuthor(User author) {
    if (author == null) {
      throw PostException.invalidField("author");
    }
  }

  private static void validateContent(String content) {
    if (content == null || content.isBlank()) {
      throw PostException.invalidField("content");
    }
    if (content.length() > MAX_CONTENT_LENGTH) {
      throw PostException.contentTooLong();
    }
  }
}
