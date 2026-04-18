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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 팔로워/팔로이 수를 미리 계산하여 저장하는 비정규화 테이블.
 *
 * <p>{@link BaseEntity} 로부터 {@code createdAt}, {@code updatedAt}, {@code deletedAt} 을 상속받는다.
 *
 * <p>카운트 갱신은 {@code FollowCountRepository} 의 원자적 UPDATE 쿼리({@code SET count = count + 1})를 사용하여 동시성
 * 문제(lost update)를 방지한다.
 */
@Entity
@Table(name = "follow_counts")
@Getter
public class FollowCount extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "user_id",
      nullable = false,
      unique = true,
      foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
  private User user;

  @Column(nullable = false)
  private long followersCount;

  @Column(nullable = false)
  private long followeesCount;

  protected FollowCount() {}

  public FollowCount(User user) {
    if (user == null) {
      throw FollowException.invalidField("user");
    }
    this.user = user;
    this.followersCount = 0;
    this.followeesCount = 0;
  }
}
