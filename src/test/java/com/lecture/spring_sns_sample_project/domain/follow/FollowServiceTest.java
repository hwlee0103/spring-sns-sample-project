package com.lecture.spring_sns_sample_project.domain.follow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lecture.spring_sns_sample_project.config.ErrorType;
import com.lecture.spring_sns_sample_project.config.TestSessionConfig;
import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * FollowService 통합 테스트.
 *
 * <p>실제 H2 DB + JPA + @Transactional + @Retryable AOP 를 거쳐 실행된다. 각 테스트는 {@link #setUp()} 에서
 * FollowCount/Follow/User 테이블을 초기화하여 독립 실행된다.
 *
 * <p>클래스 레벨 {@code @Transactional} 은 일부러 붙이지 않았다 — save() 의 DataIntegrityViolation 이 flush 시점에만
 * 발생하고, 테스트가 트랜잭션 안에 묶이면 실제 DB 상태를 검증하기 어렵기 때문.
 */
@SpringBootTest
@Import(TestSessionConfig.class)
@DisplayName("FollowService 통합 테스트")
class FollowServiceTest {

  @Autowired private FollowService followService;
  @Autowired private FollowRepository followRepository;
  @Autowired private FollowCountRepository followCountRepository;
  @Autowired private UserRepository userRepository;

  private User alice;
  private User bob;
  private User carol;

  @BeforeEach
  void setUp() {
    // 의존 순서: follow → follow_count → user
    followRepository.deleteAllInBatch();
    followCountRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();

    alice = persistUserWithCount("alice@example.com", "alice_nick");
    bob = persistUserWithCount("bob@example.com", "bob_nick");
    carol = persistUserWithCount("carol@example.com", "carol_nick");
  }

  /** User + 초기 FollowCount(0,0) 함께 생성 — UserService.register 와 동일한 후조건. */
  private User persistUserWithCount(String email, String nickname) {
    User user = new User(email, "encoded-" + nickname, nickname);
    userRepository.saveAndFlush(user);
    followCountRepository.saveAndFlush(new FollowCount(user));
    return user;
  }

  @Nested
  @DisplayName("follow()")
  class FollowMethod {

    @Test
    @DisplayName("팔로우 성공 — 활성 Follow 행 생성 + 양쪽 카운트 갱신")
    void 팔로우_성공() {
      Follow follow = followService.follow(alice.getId(), bob.getId());

      assertThat(follow.getId()).isNotNull();
      assertThat(follow.isDeleted()).isFalse();
      assertThat(follow.getDeletedAt()).isNull();
      assertThat(follow.getFollower().getId()).isEqualTo(alice.getId());
      assertThat(follow.getFollowing().getId()).isEqualTo(bob.getId());

      // 카운트: alice.followees=1, bob.followers=1
      FollowService.FollowCountResult aliceCount = followService.getFollowCount(alice.getId());
      FollowService.FollowCountResult bobCount = followService.getFollowCount(bob.getId());
      assertThat(aliceCount.followeesCount()).isEqualTo(1);
      assertThat(aliceCount.followersCount()).isZero();
      assertThat(bobCount.followersCount()).isEqualTo(1);
      assertThat(bobCount.followeesCount()).isZero();
    }

    @Test
    @DisplayName("followerId null → invalidField")
    void null_followerId() {
      assertThatThrownBy(() -> followService.follow(null, bob.getId()))
          .isInstanceOf(FollowException.class)
          .satisfies(
              e ->
                  assertThat(((FollowException) e).getErrorType())
                      .isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("followingId null → invalidField")
    void null_followingId() {
      assertThatThrownBy(() -> followService.follow(alice.getId(), null))
          .isInstanceOf(FollowException.class)
          .satisfies(
              e ->
                  assertThat(((FollowException) e).getErrorType())
                      .isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("셀프 팔로우 → selfFollow(BAD_REQUEST)")
    void 셀프_팔로우_예외() {
      assertThatThrownBy(() -> followService.follow(alice.getId(), alice.getId()))
          .isInstanceOf(FollowException.class)
          .hasMessageContaining("자기 자신을 팔로우할 수 없습니다");
    }

    @Test
    @DisplayName("follower 미존재 → userNotFound(NOT_FOUND)")
    void follower_미존재() {
      assertThatThrownBy(() -> followService.follow(99_999L, bob.getId()))
          .isInstanceOf(FollowException.class)
          .satisfies(
              e -> assertThat(((FollowException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }

    @Test
    @DisplayName("following 미존재 → userNotFound(NOT_FOUND)")
    void following_미존재() {
      assertThatThrownBy(() -> followService.follow(alice.getId(), 99_999L))
          .isInstanceOf(FollowException.class)
          .satisfies(
              e -> assertThat(((FollowException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }

    @Test
    @DisplayName("이미 활성 팔로우 상태 → alreadyFollowing(CONFLICT)")
    void 중복_팔로우_409() {
      followService.follow(alice.getId(), bob.getId());

      assertThatThrownBy(() -> followService.follow(alice.getId(), bob.getId()))
          .isInstanceOf(FollowException.class)
          .hasMessageContaining("이미 팔로우한")
          .satisfies(
              e -> assertThat(((FollowException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));

      // 카운트는 1로 유지 — 중복 호출이 카운트를 2로 올리면 안 됨
      assertThat(followService.getFollowCount(bob.getId()).followersCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("soft deleted 상태에서 재팔로우 — 기존 행 복원 + 카운트 재증가")
    void 재팔로우_복원() {
      // 팔로우 → 언팔로우 → 재팔로우
      Follow first = followService.follow(alice.getId(), bob.getId());
      Long firstId = first.getId();
      followService.unfollow(alice.getId(), bob.getId());

      assertThat(followService.getFollowCount(bob.getId()).followersCount()).isZero();

      Follow restored = followService.follow(alice.getId(), bob.getId());

      // 동일 row 복원 — INSERT 되지 않음
      assertThat(restored.getId()).isEqualTo(firstId);
      assertThat(restored.isDeleted()).isFalse();
      assertThat(restored.getDeletedAt()).isNull();
      assertThat(followService.getFollowCount(bob.getId()).followersCount()).isEqualTo(1);
      assertThat(followService.getFollowCount(alice.getId()).followeesCount()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("unfollow()")
  class UnfollowMethod {

    @Test
    @DisplayName("언팔로우 성공 — soft delete + 양쪽 카운트 감소")
    void 언팔로우_성공() {
      Follow follow = followService.follow(alice.getId(), bob.getId());
      Long followId = follow.getId();

      followService.unfollow(alice.getId(), bob.getId());

      // Row 는 남아있고 deleted=true, deletedAt 이 설정됨
      Follow afterUnfollow = followRepository.findById(followId).orElseThrow();
      assertThat(afterUnfollow.isDeleted()).isTrue();
      assertThat(afterUnfollow.getDeletedAt()).isNotNull();

      assertThat(followService.isFollowing(alice.getId(), bob.getId())).isFalse();
      assertThat(followService.getFollowCount(bob.getId()).followersCount()).isZero();
      assertThat(followService.getFollowCount(alice.getId()).followeesCount()).isZero();
    }

    @Test
    @DisplayName("팔로우하지 않은 상태 → notFollowing(BAD_REQUEST)")
    void 팔로우_안_한_상태() {
      assertThatThrownBy(() -> followService.unfollow(alice.getId(), bob.getId()))
          .isInstanceOf(FollowException.class)
          .hasMessageContaining("팔로우하지 않은");
    }

    @Test
    @DisplayName("셀프 언팔로우 → selfFollow (#136 회귀 방지)")
    void 셀프_언팔로우() {
      assertThatThrownBy(() -> followService.unfollow(alice.getId(), alice.getId()))
          .isInstanceOf(FollowException.class)
          .hasMessageContaining("자기 자신을");
    }

    @Test
    @DisplayName("followerId null → invalidField")
    void null_followerId() {
      assertThatThrownBy(() -> followService.unfollow(null, bob.getId()))
          .isInstanceOf(FollowException.class);
    }

    @Test
    @DisplayName("followingId null → invalidField")
    void null_followingId() {
      assertThatThrownBy(() -> followService.unfollow(alice.getId(), null))
          .isInstanceOf(FollowException.class);
    }

    @Test
    @DisplayName("follower 미존재 → userNotFound")
    void follower_미존재() {
      assertThatThrownBy(() -> followService.unfollow(99_999L, bob.getId()))
          .isInstanceOf(FollowException.class)
          .satisfies(
              e -> assertThat(((FollowException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }
  }

  @Nested
  @DisplayName("getFollowers()")
  class GetFollowersMethod {

    @Test
    @DisplayName("팔로워 목록 — 활성 팔로우만 반환")
    void 활성_팔로우만_반환() {
      // alice, bob 가 carol 을 팔로우
      followService.follow(alice.getId(), carol.getId());
      followService.follow(bob.getId(), carol.getId());

      Page<Follow> page = followService.getFollowers(carol.getId(), PageRequest.of(0, 10));

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(f -> f.getFollower().getId())
          .containsExactlyInAnyOrder(alice.getId(), bob.getId());
      // JOIN FETCH 확인 — 프록시가 아닌 초기화된 User 이어야 함
      assertThat(page.getContent().get(0).getFollower().getNickname()).isNotBlank();
    }

    @Test
    @DisplayName("언팔로우된(soft deleted) 행은 제외")
    void soft_deleted_제외() {
      followService.follow(alice.getId(), carol.getId());
      followService.follow(bob.getId(), carol.getId());
      followService.unfollow(alice.getId(), carol.getId());

      Page<Follow> page = followService.getFollowers(carol.getId(), PageRequest.of(0, 10));

      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().get(0).getFollower().getId()).isEqualTo(bob.getId());
    }

    @Test
    @DisplayName("미존재 사용자 → 빈 페이지 (#148 — userId 직접 쿼리 계약)")
    void 미존재_사용자는_빈_페이지() {
      Page<Follow> page = followService.getFollowers(99_999L, PageRequest.of(0, 10));

      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("페이징 — size 제한 + totalElements")
    void 페이징_동작() {
      followService.follow(alice.getId(), carol.getId());
      followService.follow(bob.getId(), carol.getId());

      Page<Follow> firstPage =
          followService.getFollowers(
              carol.getId(), PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createdAt")));

      assertThat(firstPage.getContent()).hasSize(1);
      assertThat(firstPage.getTotalElements()).isEqualTo(2);
      assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("getFollowings()")
  class GetFollowingsMethod {

    @Test
    @DisplayName("팔로잉 목록 — 활성 팔로우만 반환")
    void 활성_팔로우만_반환() {
      // alice 가 bob, carol 을 팔로우
      followService.follow(alice.getId(), bob.getId());
      followService.follow(alice.getId(), carol.getId());

      Page<Follow> page = followService.getFollowings(alice.getId(), PageRequest.of(0, 10));

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getContent())
          .extracting(f -> f.getFollowing().getId())
          .containsExactlyInAnyOrder(bob.getId(), carol.getId());
    }

    @Test
    @DisplayName("언팔로우한 대상은 제외")
    void 언팔로우_제외() {
      followService.follow(alice.getId(), bob.getId());
      followService.follow(alice.getId(), carol.getId());
      followService.unfollow(alice.getId(), bob.getId());

      Page<Follow> page = followService.getFollowings(alice.getId(), PageRequest.of(0, 10));

      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().get(0).getFollowing().getId()).isEqualTo(carol.getId());
    }

    @Test
    @DisplayName("미존재 사용자 → 빈 페이지")
    void 미존재_사용자는_빈_페이지() {
      Page<Follow> page = followService.getFollowings(99_999L, PageRequest.of(0, 10));
      assertThat(page.getContent()).isEmpty();
    }
  }

  @Nested
  @DisplayName("isFollowing()")
  class IsFollowingMethod {

    @Test
    @DisplayName("활성 팔로우 존재 → true")
    void 활성_팔로우면_true() {
      followService.follow(alice.getId(), bob.getId());

      assertThat(followService.isFollowing(alice.getId(), bob.getId())).isTrue();
    }

    @Test
    @DisplayName("soft deleted 상태 → false")
    void soft_deleted_면_false() {
      followService.follow(alice.getId(), bob.getId());
      followService.unfollow(alice.getId(), bob.getId());

      assertThat(followService.isFollowing(alice.getId(), bob.getId())).isFalse();
    }

    @Test
    @DisplayName("팔로우 관계 없음 → false")
    void 관계_없으면_false() {
      assertThat(followService.isFollowing(alice.getId(), bob.getId())).isFalse();
    }

    @Test
    @DisplayName("방향성 — A→B 팔로우 상태에서 B→A 는 false")
    void 방향성_검증() {
      followService.follow(alice.getId(), bob.getId());

      assertThat(followService.isFollowing(alice.getId(), bob.getId())).isTrue();
      assertThat(followService.isFollowing(bob.getId(), alice.getId())).isFalse();
    }

    @Test
    @DisplayName("미존재 사용자 → false (#148 계약)")
    void 미존재_사용자면_false() {
      assertThat(followService.isFollowing(alice.getId(), 99_999L)).isFalse();
      assertThat(followService.isFollowing(99_999L, bob.getId())).isFalse();
    }
  }

  @Nested
  @DisplayName("getFollowCount()")
  class GetFollowCountMethod {

    @Test
    @DisplayName("초기 카운트 (0, 0)")
    void 초기_카운트() {
      FollowService.FollowCountResult result = followService.getFollowCount(alice.getId());

      assertThat(result.followersCount()).isZero();
      assertThat(result.followeesCount()).isZero();
    }

    @Test
    @DisplayName("다중 팔로우 후 양쪽 카운트 반영")
    void 다중_팔로우_반영() {
      // alice → bob, carol   (alice.followees=2)
      // bob → alice          (bob.followees=1, alice.followers=1)
      followService.follow(alice.getId(), bob.getId());
      followService.follow(alice.getId(), carol.getId());
      followService.follow(bob.getId(), alice.getId());

      FollowService.FollowCountResult aliceCount = followService.getFollowCount(alice.getId());
      FollowService.FollowCountResult bobCount = followService.getFollowCount(bob.getId());
      FollowService.FollowCountResult carolCount = followService.getFollowCount(carol.getId());

      assertThat(aliceCount.followeesCount()).isEqualTo(2);
      assertThat(aliceCount.followersCount()).isEqualTo(1);
      assertThat(bobCount.followersCount()).isEqualTo(1);
      assertThat(bobCount.followeesCount()).isEqualTo(1);
      assertThat(carolCount.followersCount()).isEqualTo(1);
      assertThat(carolCount.followeesCount()).isZero();
    }

    @Test
    @DisplayName("미존재 사용자 → userNotFound(NOT_FOUND)")
    void 미존재_사용자() {
      assertThatThrownBy(() -> followService.getFollowCount(99_999L))
          .isInstanceOf(FollowException.class)
          .satisfies(
              e -> assertThat(((FollowException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }

    @Test
    @DisplayName("언팔로우 후 카운트 감소 — 음수로 떨어지지 않음")
    void 카운트_음수_방지() {
      followService.follow(alice.getId(), bob.getId());
      followService.unfollow(alice.getId(), bob.getId());

      assertThat(followService.getFollowCount(alice.getId()).followeesCount()).isZero();
      assertThat(followService.getFollowCount(bob.getId()).followersCount()).isZero();

      // 이미 0 인 상태에서 다시 언팔로우 시도 → notFollowing 으로 차단되어야 함
      // (decrement 쿼리는 count > 0 조건으로 보호되지만, 서비스 레벨에서 선차단)
      assertThatThrownBy(() -> followService.unfollow(alice.getId(), bob.getId()))
          .isInstanceOf(FollowException.class);

      // 여전히 0 — 음수로 떨어지지 않음
      assertThat(followService.getFollowCount(alice.getId()).followeesCount()).isZero();
      assertThat(followService.getFollowCount(bob.getId()).followersCount()).isZero();
    }
  }
}
