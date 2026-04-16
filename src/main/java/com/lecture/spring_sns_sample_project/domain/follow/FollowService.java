package com.lecture.spring_sns_sample_project.domain.follow;

import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {

  private final FollowRepository followRepository;
  private final FollowCountRepository followCountRepository;
  private final UserRepository userRepository;

  /**
   * 팔로우 — 단일 트랜잭션.
   *
   * <pre>
   * 1. 입력 검증 (null, 셀프 팔로우)
   * 2. 사용자 존재 확인
   * 3. 기존 팔로우 행 조회 (soft deleted 포함)
   *    - 활성 팔로우 존재 → 409 alreadyFollowing
   *    - soft deleted 존재 → restore (재팔로우)
   *    - 없음 → 새 Follow INSERT
   * 4. FollowCount 원자적 갱신
   * → 어느 단계에서든 실패 시 전체 롤백
   * </pre>
   */
  @Retryable(
      retryFor = TransientDataAccessException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 100, multiplier = 2))
  @Transactional
  public Follow follow(Long followerId, Long followingId) {
    // 1. 입력 검증
    if (followerId == null) {
      throw FollowException.invalidField("followerId");
    }
    if (followingId == null) {
      throw FollowException.invalidField("followingId");
    }
    if (followerId.equals(followingId)) {
      throw FollowException.selfFollow();
    }

    // 2. 사용자 존재 확인
    User follower =
        userRepository
            .findById(followerId)
            .orElseThrow(() -> FollowException.userNotFound(followerId));
    User following =
        userRepository
            .findById(followingId)
            .orElseThrow(() -> FollowException.userNotFound(followingId));

    // 3. 기존 행 조회 (deleted 상관없이)
    Optional<Follow> existing = followRepository.findByFollowerAndFollowing(follower, following);

    Follow follow;
    if (existing.isPresent()) {
      Follow found = existing.get();
      if (!found.isDeleted()) {
        throw FollowException.alreadyFollowing();
      }
      // soft deleted → 복원 (재팔로우)
      found.restore();
      follow = found;
    } else {
      // 새 Follow 생성
      follow = followRepository.save(new Follow(follower, following));
    }

    // 4. FollowCount 원자적 갱신 — user_id 오름차순 (데드락 방지)
    updateFollowCounts(followerId, followingId, true);

    return follow;
  }

  /**
   * 언팔로우 — soft delete + 단일 트랜잭션.
   *
   * <pre>
   * 1. 입력 검증
   * 2. 사용자 존재 확인
   * 3. 활성 팔로우 조회 (deleted=false)
   * 4. soft delete (deleted=true, deletedAt=now)
   * 5. FollowCount 원자적 갱신
   * </pre>
   */
  @Retryable(
      retryFor = TransientDataAccessException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 100, multiplier = 2))
  @Transactional
  public void unfollow(Long followerId, Long followingId) {
    if (followerId == null) {
      throw FollowException.invalidField("followerId");
    }
    if (followingId == null) {
      throw FollowException.invalidField("followingId");
    }

    User follower =
        userRepository
            .findById(followerId)
            .orElseThrow(() -> FollowException.userNotFound(followerId));
    User following =
        userRepository
            .findById(followingId)
            .orElseThrow(() -> FollowException.userNotFound(followingId));

    Follow follow =
        followRepository
            .findByFollowerAndFollowingAndDeletedFalse(follower, following)
            .orElseThrow(FollowException::notFollowing);

    follow.softDelete();

    updateFollowCounts(followerId, followingId, false);
  }

  /** 팔로워 목록 — 활성 팔로우만. */
  public Page<Follow> getFollowers(Long userId, Pageable pageable) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> FollowException.userNotFound(userId));
    return followRepository.findActiveFollowersByUser(user, pageable);
  }

  /** 팔로잉 목록 — 활성 팔로우만. */
  public Page<Follow> getFollowings(Long userId, Pageable pageable) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> FollowException.userNotFound(userId));
    return followRepository.findActiveFollowingsByUser(user, pageable);
  }

  /** 팔로우 여부 확인 — 활성 팔로우만. */
  public boolean isFollowing(Long followerId, Long followingId) {
    User follower =
        userRepository
            .findById(followerId)
            .orElseThrow(() -> FollowException.userNotFound(followerId));
    User following =
        userRepository
            .findById(followingId)
            .orElseThrow(() -> FollowException.userNotFound(followingId));
    return followRepository.existsByFollowerAndFollowingAndDeletedFalse(follower, following);
  }

  /** 팔로워/팔로잉 카운트 — FollowCount 테이블에서 O(1) 조회. */
  public FollowCountResult getFollowCount(Long userId) {
    FollowCount count =
        followCountRepository
            .findByUserId(userId)
            .orElseThrow(() -> FollowException.userNotFound(userId));
    return new FollowCountResult(count.getFollowersCount(), count.getFolloweesCount());
  }

  /** 팔로워/팔로잉 수 — Service 반환 값 객체. */
  public record FollowCountResult(long followerCount, long followeesCount) {}

  /**
   * FollowCount 원자적 갱신 — user_id 오름차순으로 UPDATE 하여 데드락을 방지한다.
   *
   * @param increment true 면 +1 (팔로우), false 면 -1 (언팔로우)
   */
  private void updateFollowCounts(Long followerId, Long followingId, boolean increment) {
    Long smallerId = Math.min(followerId, followingId);

    if (increment) {
      if (smallerId.equals(followerId)) {
        followCountRepository.incrementFolloweesCount(followerId);
        followCountRepository.incrementFollowersCount(followingId);
      } else {
        followCountRepository.incrementFollowersCount(followingId);
        followCountRepository.incrementFolloweesCount(followerId);
      }
    } else {
      if (smallerId.equals(followerId)) {
        followCountRepository.decrementFolloweesCount(followerId);
        followCountRepository.decrementFollowersCount(followingId);
      } else {
        followCountRepository.decrementFollowersCount(followingId);
        followCountRepository.decrementFolloweesCount(followerId);
      }
    }
  }
}
