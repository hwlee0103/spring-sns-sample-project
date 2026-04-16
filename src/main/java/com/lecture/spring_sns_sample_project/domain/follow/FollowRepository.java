package com.lecture.spring_sns_sample_project.domain.follow;

import com.lecture.spring_sns_sample_project.domain.user.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

  /** deleted 상관없이 조회 — 재팔로우 시 기존 행 복원에 사용. */
  Optional<Follow> findByFollowerAndFollowing(User follower, User following);

  /** 활성 팔로우만 존재 여부 확인. */
  boolean existsByFollowerAndFollowingAndDeletedFalse(User follower, User following);

  /** 활성 팔로우만 조회. */
  Optional<Follow> findByFollowerAndFollowingAndDeletedFalse(User follower, User following);

  /** 팔로워 목록 — 활성 팔로우만. */
  @Query(
      "SELECT f FROM Follow f JOIN FETCH f.follower WHERE f.following = :user AND f.deleted = false")
  Page<Follow> findActiveFollowersByUser(@Param("user") User user, Pageable pageable);

  /** 팔로잉 목록 — 활성 팔로우만. */
  @Query(
      "SELECT f FROM Follow f JOIN FETCH f.following WHERE f.follower = :user AND f.deleted = false")
  Page<Follow> findActiveFollowingsByUser(@Param("user") User user, Pageable pageable);
}
