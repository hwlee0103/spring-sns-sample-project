package com.lecture.spring_sns_sample_project.domain.follow;

import com.lecture.spring_sns_sample_project.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(
    name = "follows",
    uniqueConstraints = @UniqueConstraint(columnNames = {"follower_id", "following_id"}))
@Getter
public class Follow {

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

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

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
    this.createdAt = Instant.now();
  }
}
