package com.lecture.spring_sns_sample_project.domain.follow;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowCountRepository extends JpaRepository<FollowCount, Long> {

  Optional<FollowCount> findByUserId(Long userId);

  /**
   * 팔로워 수 +1 — DB 원자적 UPDATE 로 lost update 방지.
   *
   * @return affected rows — 0 이면 FollowCount 행 부재 (호출자가 처리 필요)
   */
  @Modifying
  @Query(
      "UPDATE FollowCount fc SET fc.followersCount = fc.followersCount + 1 WHERE fc.user.id = :userId")
  int incrementFollowersCount(@Param("userId") Long userId);

  /** 팔로워 수 -1 — 음수 방지 조건 포함. */
  @Modifying
  @Query(
      "UPDATE FollowCount fc SET fc.followersCount = fc.followersCount - 1 WHERE fc.user.id = :userId AND fc.followersCount > 0")
  int decrementFollowersCount(@Param("userId") Long userId);

  /** 팔로이 수 +1. */
  @Modifying
  @Query(
      "UPDATE FollowCount fc SET fc.followeesCount = fc.followeesCount + 1 WHERE fc.user.id = :userId")
  int incrementFolloweesCount(@Param("userId") Long userId);

  /** 팔로이 수 -1. */
  @Modifying
  @Query(
      "UPDATE FollowCount fc SET fc.followeesCount = fc.followeesCount - 1 WHERE fc.user.id = :userId AND fc.followeesCount > 0")
  int decrementFolloweesCount(@Param("userId") Long userId);
}
