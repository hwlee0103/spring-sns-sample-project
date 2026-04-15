package com.lecture.spring_sns_sample_project.domain.follow;

import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
   * 2. 사용자 존재 확인 (follower, following)
   * 3. 이미 팔로우 중인지 검증
   * 4. Follow 저장
   * 5. FollowCount 원자적 갱신 (follower: followeesCount +1, following: followersCount +1)
   * 6. 성공 리턴
   * → 어느 단계에서든 실패 시 전체 롤백
   * </pre>
   */
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

    // 3. 이미 팔로우 중인지 검증
    if (followRepository.existsByFollowerAndFollowing(follower, following)) {
      throw FollowException.alreadyFollowing();
    }

    // 4. Follow 저장
    Follow follow = new Follow(follower, following);
    try {
      followRepository.save(follow);
    } catch (DataIntegrityViolationException e) {
      throw FollowException.alreadyFollowing();
    }

    // 5. FollowCount 원자적 갱신
    followCountRepository.incrementFolloweesCount(followerId);
    followCountRepository.incrementFollowersCount(followingId);

    // 6. 성공 리턴
    return follow;
  }

  /**
   * 언팔로우 — 단일 트랜잭션.
   *
   * <pre>
   * 1. 입력 검증
   * 2. 사용자 존재 확인
   * 3. 팔로우 관계 조회 (없으면 예외)
   * 4. Follow 삭제
   * 5. FollowCount 원자적 갱신 (follower: followeesCount -1, following: followersCount -1)
   * → 어느 단계에서든 실패 시 전체 롤백
   * </pre>
   */
  @Transactional
  public void unfollow(Long followerId, Long followingId) {
    // 1. 입력 검증
    if (followerId == null) {
      throw FollowException.invalidField("followerId");
    }
    if (followingId == null) {
      throw FollowException.invalidField("followingId");
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

    // 3. 팔로우 관계 조회
    Follow follow =
        followRepository
            .findByFollowerAndFollowing(follower, following)
            .orElseThrow(FollowException::notFollowing);

    // 4. Follow 삭제
    followRepository.delete(follow);

    // 5. FollowCount 원자적 갱신
    followCountRepository.decrementFolloweesCount(followerId);
    followCountRepository.decrementFollowersCount(followingId);
  }

  /** 팔로워 목록 — 특정 사용자를 팔로우하는 사람들. */
  public Page<Follow> getFollowers(Long userId, Pageable pageable) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> FollowException.userNotFound(userId));
    return followRepository.findFollowersByUser(user, pageable);
  }

  /** 팔로잉 목록 — 특정 사용자가 팔로우하는 사람들. */
  public Page<Follow> getFollowings(Long userId, Pageable pageable) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> FollowException.userNotFound(userId));
    return followRepository.findFollowingsByUser(user, pageable);
  }

  /** 팔로우 여부 확인. */
  public boolean isFollowing(Long followerId, Long followingId) {
    User follower =
        userRepository
            .findById(followerId)
            .orElseThrow(() -> FollowException.userNotFound(followerId));
    User following =
        userRepository
            .findById(followingId)
            .orElseThrow(() -> FollowException.userNotFound(followingId));
    return followRepository.existsByFollowerAndFollowing(follower, following);
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
}
