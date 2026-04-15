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
  private final UserRepository userRepository;

  /** 팔로우. */
  public Follow follow(Long followerId, Long followingId) {
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

    if (followRepository.existsByFollowerAndFollowing(follower, following)) {
      throw FollowException.alreadyFollowing();
    }

    Follow follow = new Follow(follower, following);
    try {
      return followRepository.save(follow);
    } catch (DataIntegrityViolationException e) {
      throw FollowException.alreadyFollowing();
    }
  }

  /** 언팔로우. */
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
            .findByFollowerAndFollowing(follower, following)
            .orElseThrow(FollowException::notFollowing);
    followRepository.delete(follow);
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

  /** 팔로워/팔로잉 카운트. */
  public FollowCount getFollowCount(Long userId) {
    User user =
        userRepository.findById(userId).orElseThrow(() -> FollowException.userNotFound(userId));
    long followerCount = followRepository.countByFollowing(user);
    long followingCount = followRepository.countByFollower(user);
    return new FollowCount(followerCount, followingCount);
  }

  /** 팔로워/팔로잉 수 — Service 내부 값 객체. */
  public record FollowCount(long followerCount, long followingCount) {}
}
