package com.lecture.spring_sns_sample_project.domain.follow;

import com.lecture.spring_sns_sample_project.domain.user.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

  boolean existsByFollowerAndFollowing(User follower, User following);

  Optional<Follow> findByFollowerAndFollowing(User follower, User following);

  /** 팔로워 목록 — 특정 사용자를 팔로우하는 사람들. */
  @Query("SELECT f FROM Follow f JOIN FETCH f.follower WHERE f.following = :user")
  Page<Follow> findFollowersByUser(@Param("user") User user, Pageable pageable);

  /** 팔로잉 목록 — 특정 사용자가 팔로우하는 사람들. */
  @Query("SELECT f FROM Follow f JOIN FETCH f.following WHERE f.follower = :user")
  Page<Follow> findFollowingsByUser(@Param("user") User user, Pageable pageable);

  long countByFollowing(User following);

  long countByFollower(User follower);
}
