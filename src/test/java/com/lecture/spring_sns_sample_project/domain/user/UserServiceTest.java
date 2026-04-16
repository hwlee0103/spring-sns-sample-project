package com.lecture.spring_sns_sample_project.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lecture.spring_sns_sample_project.config.ErrorType;
import com.lecture.spring_sns_sample_project.config.TestSessionConfig;
import com.lecture.spring_sns_sample_project.domain.follow.Follow;
import com.lecture.spring_sns_sample_project.domain.follow.FollowCount;
import com.lecture.spring_sns_sample_project.domain.follow.FollowCountRepository;
import com.lecture.spring_sns_sample_project.domain.follow.FollowRepository;
import com.lecture.spring_sns_sample_project.domain.post.Post;
import com.lecture.spring_sns_sample_project.domain.post.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * UserService 통합 테스트.
 *
 * <p>회원가입 시 FollowCount 초기행 동반 생성(#3.5), 중복 검증(#114), 비밀번호 변경 tokenVersion 증가(#94), cascade
 * 삭제(#143) 등 UserService 의 cross-aggregate 후조건을 실제 JPA 로 검증한다.
 */
@SpringBootTest
@Import(TestSessionConfig.class)
@DisplayName("UserService 통합 테스트")
class UserServiceTest {

  @Autowired private UserService userService;
  @Autowired private UserRepository userRepository;
  @Autowired private FollowRepository followRepository;
  @Autowired private FollowCountRepository followCountRepository;
  @Autowired private PostRepository postRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    followRepository.deleteAllInBatch();
    postRepository.deleteAllInBatch();
    followCountRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }

  @Nested
  @DisplayName("register()")
  class Register {

    @Test
    @DisplayName("정상 가입 — User 저장 + FollowCount(0,0) 초기행 동반 생성")
    void 정상_가입() {
      User user = userService.register("alice@example.com", "password123", "alice_nick");

      assertThat(user.getId()).isNotNull();
      assertThat(user.getEmail()).isEqualTo("alice@example.com");
      assertThat(user.getNickname()).isEqualTo("alice_nick");
      assertThat(user.getRole()).isEqualTo(Role.USER);
      assertThat(user.getTokenVersion()).isZero();

      // FollowCount 초기행 (#3.5)
      FollowCount count = followCountRepository.findByUserId(user.getId()).orElseThrow();
      assertThat(count.getFollowersCount()).isZero();
      assertThat(count.getFolloweesCount()).isZero();
    }

    @Test
    @DisplayName("비밀번호는 인코딩되어 저장 — raw 값 비교 시 matches=true")
    void 비밀번호_인코딩_저장() {
      User user = userService.register("alice@example.com", "raw_password", "alice_nick");

      assertThat(user.getPassword()).isNotEqualTo("raw_password");
      assertThat(passwordEncoder.matches("raw_password", user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("email null → invalidField")
    void email_null() {
      assertThatThrownBy(() -> userService.register(null, "password123", "nick"))
          .isInstanceOf(UserException.class)
          .satisfies(
              e -> assertThat(((UserException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
    }

    @Test
    @DisplayName("email blank → invalidField")
    void email_blank() {
      assertThatThrownBy(() -> userService.register("   ", "password123", "nick"))
          .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("password null → invalidField")
    void password_null() {
      assertThatThrownBy(() -> userService.register("a@b.com", null, "nick"))
          .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("password blank → invalidField")
    void password_blank() {
      assertThatThrownBy(() -> userService.register("a@b.com", "  ", "nick"))
          .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("nickname null → invalidField")
    void nickname_null() {
      assertThatThrownBy(() -> userService.register("a@b.com", "password123", null))
          .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("nickname blank → invalidField")
    void nickname_blank() {
      assertThatThrownBy(() -> userService.register("a@b.com", "password123", "  "))
          .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("중복 email → emailAlreadyExists(CONFLICT)")
    void 중복_email() {
      userService.register("alice@example.com", "password123", "alice_nick");

      assertThatThrownBy(
              () -> userService.register("alice@example.com", "password456", "other_nick"))
          .isInstanceOf(UserException.class)
          .hasMessageContaining("이미 존재하는 이메일")
          .satisfies(
              e -> assertThat(((UserException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
    }

    @Test
    @DisplayName("중복 nickname → nicknameAlreadyExists(CONFLICT) (#114)")
    void 중복_nickname() {
      userService.register("alice@example.com", "password123", "alice_nick");

      assertThatThrownBy(() -> userService.register("bob@example.com", "password456", "alice_nick"))
          .isInstanceOf(UserException.class)
          .hasMessageContaining("이미 존재하는 닉네임")
          .satisfies(
              e -> assertThat(((UserException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
    }
  }

  @Nested
  @DisplayName("getById() / getByEmail() / getByNickname()")
  class GetByXxx {

    @Test
    @DisplayName("getById — 정상 조회")
    void getById_정상() {
      User saved = userService.register("a@b.com", "password123", "alice");

      User found = userService.getById(saved.getId());

      assertThat(found.getId()).isEqualTo(saved.getId());
      assertThat(found.getEmail()).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("getById — 미존재 → NOT_FOUND")
    void getById_미존재() {
      assertThatThrownBy(() -> userService.getById(99_999L))
          .isInstanceOf(UserException.class)
          .satisfies(
              e -> assertThat(((UserException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }

    @Test
    @DisplayName("getByEmail — 정상 조회")
    void getByEmail_정상() {
      userService.register("a@b.com", "password123", "alice");

      User found = userService.getByEmail("a@b.com");

      assertThat(found.getNickname()).isEqualTo("alice");
    }

    @Test
    @DisplayName("getByEmail — 미존재 → NOT_FOUND")
    void getByEmail_미존재() {
      assertThatThrownBy(() -> userService.getByEmail("missing@example.com"))
          .isInstanceOf(UserException.class)
          .satisfies(
              e -> assertThat(((UserException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }

    @Test
    @DisplayName("getByNickname — 정상 조회")
    void getByNickname_정상() {
      userService.register("a@b.com", "password123", "alice");

      User found = userService.getByNickname("alice");

      assertThat(found.getEmail()).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("getByNickname — 미존재 → NOT_FOUND")
    void getByNickname_미존재() {
      assertThatThrownBy(() -> userService.getByNickname("missing"))
          .isInstanceOf(UserException.class);
    }
  }

  @Nested
  @DisplayName("getAll()")
  class GetAll {

    @Test
    @DisplayName("페이징 — size 제한 + totalElements")
    void 페이징_동작() {
      userService.register("a@b.com", "password123", "alice");
      userService.register("b@b.com", "password123", "bob");
      userService.register("c@b.com", "password123", "carol");

      Page<User> page = userService.getAll(PageRequest.of(0, 2));

      assertThat(page.getContent()).hasSize(2);
      assertThat(page.getTotalElements()).isEqualTo(3);
      assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("빈 결과 — 사용자 없을 때 empty page")
    void 빈_결과() {
      Page<User> page = userService.getAll(PageRequest.of(0, 10));

      assertThat(page.getContent()).isEmpty();
      assertThat(page.getTotalElements()).isZero();
    }
  }

  @Nested
  @DisplayName("update()")
  class Update {

    @Test
    @DisplayName("정상 nickname 변경 — password 는 변경되지 않음")
    void 정상_변경() {
      User user = userService.register("a@b.com", "password123", "old_nick");
      String originalPassword = user.getPassword();

      User updated = userService.update(user.getId(), "new_nick");

      assertThat(updated.getNickname()).isEqualTo("new_nick");
      // password 는 changePassword 전용이므로 update 에서 건드려서는 안 됨
      assertThat(updated.getPassword()).isEqualTo(originalPassword);
    }

    @Test
    @DisplayName("nickname null → invalidField")
    void nickname_null() {
      User user = userService.register("a@b.com", "password123", "nick");

      assertThatThrownBy(() -> userService.update(user.getId(), null))
          .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("nickname blank → invalidField")
    void nickname_blank() {
      User user = userService.register("a@b.com", "password123", "nick");

      assertThatThrownBy(() -> userService.update(user.getId(), "  "))
          .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("동일 nickname 유지 허용 — 본인이 본인 nickname 으로 PUT 시 409 나면 안 됨 (#130)")
    void 동일_nickname_허용() {
      User user = userService.register("a@b.com", "password123", "same_nick");

      User updated = userService.update(user.getId(), "same_nick");

      assertThat(updated.getNickname()).isEqualTo("same_nick");
    }

    @Test
    @DisplayName("중복 nickname — 타인의 nickname 으로 변경 시 409 (#130)")
    void 중복_nickname_409() {
      userService.register("a@b.com", "password123", "alice");
      User bob = userService.register("b@b.com", "password123", "bob");

      assertThatThrownBy(() -> userService.update(bob.getId(), "alice"))
          .isInstanceOf(UserException.class)
          .satisfies(
              e -> assertThat(((UserException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
    }

    @Test
    @DisplayName("미존재 user → NOT_FOUND")
    void 미존재_user() {
      assertThatThrownBy(() -> userService.update(99_999L, "new_nick"))
          .isInstanceOf(UserException.class)
          .satisfies(
              e -> assertThat(((UserException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }
  }

  @Nested
  @DisplayName("changePassword()")
  class ChangePassword {

    @Test
    @DisplayName("정상 변경 — tokenVersion 1 증가 + 새 비번 인코딩")
    void 정상_변경() {
      User user = userService.register("a@b.com", "old_password", "nick");
      int beforeVersion = user.getTokenVersion();

      User updated = userService.changePassword(user.getId(), "old_password", "new_password");

      assertThat(updated.getTokenVersion()).isEqualTo(beforeVersion + 1);
      assertThat(passwordEncoder.matches("new_password", updated.getPassword())).isTrue();
      assertThat(passwordEncoder.matches("old_password", updated.getPassword())).isFalse();
    }

    @Test
    @DisplayName("현재 비번 오류 → invalidCredentials(BAD_REQUEST)")
    void 현재비번_오류() {
      User user = userService.register("a@b.com", "old_password", "nick");

      assertThatThrownBy(
              () -> userService.changePassword(user.getId(), "wrong_password", "new_password"))
          .isInstanceOf(UserException.class)
          .hasMessageContaining("현재 비밀번호");

      // 실패 시 tokenVersion/password 둘 다 유지
      User reloaded = userRepository.findById(user.getId()).orElseThrow();
      assertThat(reloaded.getTokenVersion()).isEqualTo(user.getTokenVersion());
      assertThat(passwordEncoder.matches("old_password", reloaded.getPassword())).isTrue();
    }

    @Test
    @DisplayName("동일 비번 → samePassword(BAD_REQUEST) (#121)")
    void 동일_비번() {
      User user = userService.register("a@b.com", "password123", "nick");

      assertThatThrownBy(
              () -> userService.changePassword(user.getId(), "password123", "password123"))
          .isInstanceOf(UserException.class)
          .hasMessageContaining("현재 비밀번호와 달라야");

      // tokenVersion 그대로
      User reloaded = userRepository.findById(user.getId()).orElseThrow();
      assertThat(reloaded.getTokenVersion()).isZero();
    }

    @Test
    @DisplayName("current null → invalidField")
    void current_null() {
      User user = userService.register("a@b.com", "password123", "nick");

      assertThatThrownBy(() -> userService.changePassword(user.getId(), null, "new_password"))
          .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("new null → invalidField")
    void new_null() {
      User user = userService.register("a@b.com", "password123", "nick");

      assertThatThrownBy(() -> userService.changePassword(user.getId(), "password123", null))
          .isInstanceOf(UserException.class);
    }

    @Test
    @DisplayName("미존재 user → NOT_FOUND")
    void 미존재_user() {
      assertThatThrownBy(() -> userService.changePassword(99_999L, "password123", "new_password"))
          .isInstanceOf(UserException.class)
          .satisfies(
              e -> assertThat(((UserException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }
  }

  @Nested
  @DisplayName("delete() — cascade 정리 (#143)")
  class Delete {

    @Test
    @DisplayName("정상 삭제 — User 행 제거")
    void 정상_삭제() {
      User user = userService.register("a@b.com", "password123", "alice");

      userService.delete(user.getId());

      assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("cascade — FollowCount 삭제")
    void cascade_follow_count() {
      User user = userService.register("a@b.com", "password123", "alice");
      // register 가 FollowCount(0,0) 을 이미 생성
      assertThat(followCountRepository.findByUserId(user.getId())).isPresent();

      userService.delete(user.getId());

      assertThat(followCountRepository.findByUserId(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("cascade — 본인이 쓴 게시글 삭제")
    void cascade_post() {
      User user = userService.register("a@b.com", "password123", "alice");
      Post post = new Post(user, "hello world");
      postRepository.save(post);
      assertThat(postRepository.findById(post.getId())).isPresent();

      userService.delete(user.getId());

      assertThat(postRepository.findById(post.getId())).isEmpty();
    }

    @Test
    @DisplayName("cascade — 양방향 팔로우 관계 모두 삭제")
    void cascade_follow_양방향() {
      User alice = userService.register("a@b.com", "password123", "alice");
      User bob = userService.register("b@b.com", "password123", "bob");
      User carol = userService.register("c@b.com", "password123", "carol");

      // alice → bob, carol → alice (bob 계정 중심으로 양방향)
      followRepository.save(new Follow(alice, bob));
      followRepository.save(new Follow(carol, bob));
      followRepository.save(new Follow(bob, alice));
      followRepository.save(new Follow(bob, carol));
      assertThat(followRepository.count()).isEqualTo(4);

      userService.delete(bob.getId());

      // bob 이 follower 이거나 following 인 모든 행 삭제 (4개 중 4개)
      assertThat(followRepository.count()).isZero();
    }

    @Test
    @DisplayName("다른 사용자의 관련 레코드는 보존됨 — 격리")
    void 다른_사용자_보존() {
      User alice = userService.register("a@b.com", "password123", "alice");
      User bob = userService.register("b@b.com", "password123", "bob");
      Post alicePost = postRepository.save(new Post(alice, "alice post"));
      Post bobPost = postRepository.save(new Post(bob, "bob post"));

      userService.delete(alice.getId());

      // bob 의 레코드는 그대로
      assertThat(userRepository.findById(bob.getId())).isPresent();
      assertThat(followCountRepository.findByUserId(bob.getId())).isPresent();
      assertThat(postRepository.findById(bobPost.getId())).isPresent();
      // alice 의 레코드만 삭제
      assertThat(postRepository.findById(alicePost.getId())).isEmpty();
    }

    @Test
    @DisplayName("미존재 user → NOT_FOUND")
    void 미존재_user() {
      assertThatThrownBy(() -> userService.delete(99_999L))
          .isInstanceOf(UserException.class)
          .satisfies(
              e -> assertThat(((UserException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }
  }
}
