package com.lecture.spring_sns_sample_project.domain.follow;

import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    // 2-1. 마이그레이션 이전 계정 방어 — FollowCount 행 보장
    ensureFollowCountExists(follower);
    ensureFollowCountExists(following);

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
      // 새 Follow 생성 — 동시 요청 race 시 UNIQUE 위반 → 409 변환
      try {
        follow = followRepository.save(new Follow(follower, following));
      } catch (DataIntegrityViolationException e) {
        throw FollowException.alreadyFollowing();
      }
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
    if (followerId.equals(followingId)) {
      throw FollowException.selfFollow();
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

    ensureFollowCountExists(follower);
    ensureFollowCountExists(following);
    updateFollowCounts(followerId, followingId, false);
  }

  /** FollowCount 행이 없으면 생성 — 마이그레이션 이전 계정 보호. */
  private void ensureFollowCountExists(User user) {
    if (followCountRepository.findByUserId(user.getId()).isEmpty()) {
      try {
        followCountRepository.save(new FollowCount(user));
      } catch (DataIntegrityViolationException ignored) {
        // 동시 요청이 이미 생성함 — 무시
      }
    }
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

  /** 팔로워/팔로이 수 — Service 반환 값 객체. 도메인(FollowCount) 네이밍과 통일. */
  public record FollowCountResult(long followersCount, long followeesCount) {}

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
