package com.lecture.spring_sns_sample_project.domain.follow;

import com.lecture.spring_sns_sample_project.domain.user.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

  /**
   * deleted 상관없이 조회 + 비관적 쓰기 잠금 — 재팔로우 시 기존 행 복원에 사용.
   *
   * <p>동시 재팔로우 요청 시 두 스레드가 모두 {@code isDeleted()==true} 를 읽고 {@code restore()} + 카운트
   * double-increment 하는 것을 방지한다. 행 잠금으로 한 스레드만 restore 를 수행하고, 다른 스레드는 {@code isDeleted()==false} 를
   * 읽어 {@code alreadyFollowing} 을 반환한다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Follow> findByFollowerAndFollowing(User follower, User following);

  /** 활성 팔로우만 존재 여부 확인. */
  boolean existsByFollowerAndFollowingAndDeletedFalse(User follower, User following);

  /** 활성 팔로우 존재 여부 확인 — userId 직접. User 조회 없이 1쿼리로 수행. */
  boolean existsByFollowerIdAndFollowingIdAndDeletedFalse(Long followerId, Long followingId);

  /** 활성 팔로우만 조회. */
  Optional<Follow> findByFollowerAndFollowingAndDeletedFalse(User follower, User following);

  /**
   * 팔로워 목록 — 활성 팔로우만. fetch join 으로 follower User 즉시 로딩(N+1 방지). userId 직접 쿼리 — 사전 User 조회 불필요.
   *
   * <p>countQuery 명시로 페이징 성능 최적화 — JOIN FETCH 제외한 COUNT 만 실행.
   */
  @Query(
      value =
          "SELECT f FROM Follow f JOIN FETCH f.follower WHERE f.following.id = :userId AND f.deleted = false",
      countQuery =
          "SELECT COUNT(f) FROM Follow f WHERE f.following.id = :userId AND f.deleted = false")
  Page<Follow> findActiveFollowersByUserId(@Param("userId") Long userId, Pageable pageable);

  /** 팔로잉 목록 — 활성 팔로우만. fetch join 으로 following User 즉시 로딩. userId 직접 쿼리. */
  @Query(
      value =
          "SELECT f FROM Follow f JOIN FETCH f.following WHERE f.follower.id = :userId AND f.deleted = false",
      countQuery =
          "SELECT COUNT(f) FROM Follow f WHERE f.follower.id = :userId AND f.deleted = false")
  Page<Follow> findActiveFollowingsByUserId(@Param("userId") Long userId, Pageable pageable);

  /** 사용자 삭제 시 양방향 팔로우 관계 물리 삭제. */
  @org.springframework.data.jpa.repository.Modifying
  @Query("DELETE FROM Follow f WHERE f.follower = :user OR f.following = :user")
  void deleteAllByUser(@Param("user") User user);
}
