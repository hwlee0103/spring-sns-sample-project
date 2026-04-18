package com.lecture.spring_sns_sample_project.domain.follow;

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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

/**
 * 팔로우 관계 Entity — Soft Delete 패턴.
 *
 * <p>{@link BaseEntity} 로부터 {@code createdAt}, {@code updatedAt}, {@code deletedAt} 을 상속받는다. {@code
 * deleted} boolean 은 Follow 전용 쿼리 필터링에 사용(인덱스 지원).
 *
 * <p>{@code restore()} 시 {@code createdAt} 을 현재 시각으로 재설정하여 "재팔로우 시점"을 기록한다.
 */
@Entity
@Table(
    name = "follows",
    uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "following_id"}),
    indexes = {
      @Index(name = "idx_follows_following_id", columnList = "following_id"),
      @Index(name = "idx_follows_following_created", columnList = "following_id, created_at DESC"),
      @Index(name = "idx_follows_follower_created", columnList = "follower_id, created_at DESC")
    })
@Getter
public class Follow extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "follower_id",
      nullable = false,
      foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
  private User follower;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "following_id",
      nullable = false,
      foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
  private User following;

  @Column(nullable = false)
  private boolean deleted = false;

  protected Follow() {}

  public Follow(User follower, User following) {
    if (follower == null) {
      throw FollowException.invalidField("follower");
    }
    if (following == null) {
      throw FollowException.invalidField("following");
    }
    if (follower.getId().equals(following.getId())) {
      throw FollowException.selfFollow();
    }
    this.follower = follower;
    this.following = following;
  }

  /** 언팔로우 — 논리 삭제. */
  public void softDelete() {
    this.deleted = true;
    this.deletedAt = Instant.now();
  }

  /** 재팔로우 — soft deleted 행 복원. {@code createdAt} 을 현재 시각으로 재설정. */
  public void restore() {
    this.deleted = false;
    this.deletedAt = null;
    this.createdAt = Instant.now();
  }
}
