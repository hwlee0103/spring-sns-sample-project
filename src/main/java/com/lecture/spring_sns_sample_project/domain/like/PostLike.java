package com.lecture.spring_sns_sample_project.domain.like;

import com.lecture.spring_sns_sample_project.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

/**
 * 좋아요(리액션) 도메인 Entity.
 *
 * <p>게시글과 조회 패턴이 다르므로 별도 {@code post_likes} 테이블. {@code UNIQUE (user_id, post_id)} 로 중복 좋아요를 DB
 * 레벨에서 차단한다.
 *
 * <p>Soft delete + restore 패턴 적용 — Follow 도메인과 동일. 취소 후 재좋아요 시 기존 행을 복원한다.
 */
@Entity
@Table(
    name = "post_likes",
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

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ReactionType reaction;

  protected PostLike() {}

  public PostLike(Long userId, Long postId) {
    this(userId, postId, ReactionType.LIKE);
  }

  public PostLike(Long userId, Long postId, ReactionType reaction) {
    if (userId == null) {
      throw PostLikeException.invalidField("userId");
    }
    if (postId == null) {
      throw PostLikeException.invalidField("postId");
    }
    if (reaction == null) {
      throw PostLikeException.invalidField("reaction");
    }
    this.userId = userId;
    this.postId = postId;
    this.reaction = reaction;
  }

  /** 리액션 변경 (향후: LIKE → LOVE 등). */
  public void changeReaction(ReactionType newReaction) {
    if (newReaction == null) {
      throw PostLikeException.invalidField("reaction");
    }
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
