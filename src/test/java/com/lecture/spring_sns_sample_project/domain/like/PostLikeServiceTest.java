package com.lecture.spring_sns_sample_project.domain.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lecture.spring_sns_sample_project.config.ErrorType;
import com.lecture.spring_sns_sample_project.config.TestSessionConfig;
import com.lecture.spring_sns_sample_project.domain.post.Post;
import com.lecture.spring_sns_sample_project.domain.post.PostRepository;
import com.lecture.spring_sns_sample_project.domain.post.PostService;
import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * PostLikeService 통합 테스트.
 *
 * <p>좋아요 생성/취소(soft delete)/재좋아요(restore)/중복 방지/카운트 연동을 실제 JPA 로 검증.
 */
@SpringBootTest
@Import(TestSessionConfig.class)
@DisplayName("PostLikeService 통합 테스트")
class PostLikeServiceTest {

  @Autowired private PostLikeService postLikeService;
  @Autowired private PostLikeRepository postLikeRepository;
  @Autowired private PostService postService;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;

  private User alice;
  private User bob;
  private Post post;

  @BeforeEach
  void setUp() {
    postLikeRepository.deleteAllInBatch();
    postRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();

    alice = userRepository.saveAndFlush(new User("alice@example.com", "encoded-alice", "alice"));
    bob = userRepository.saveAndFlush(new User("bob@example.com", "encoded-bob", "bob"));
    post = postService.create(alice.getId(), "test post", null, null, null);
  }

  @Nested
  @DisplayName("like()")
  class Like {

    @Test
    @DisplayName("좋아요 성공 + Post.likeCount 증가")
    void 좋아요_성공() {
      PostLike like = postLikeService.like(bob.getId(), post.getId());

      assertThat(like.getId()).isNotNull();
      assertThat(like.getUserId()).isEqualTo(bob.getId());
      assertThat(like.getPostId()).isEqualTo(post.getId());
      assertThat(like.getReaction()).isEqualTo(ReactionType.LIKE);
      assertThat(like.isCancelled()).isFalse();

      Post updatedPost = postService.getById(post.getId());
      assertThat(updatedPost.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글 미존재 → notFound")
    void 게시글_미존재() {
      assertThatThrownBy(() -> postLikeService.like(bob.getId(), 99_999L))
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("삭제된 게시글 좋아요 → notFound (#168)")
    void 삭제된_게시글() {
      postService.delete(alice.getId(), post.getId());

      assertThatThrownBy(() -> postLikeService.like(bob.getId(), post.getId()))
          .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("중복 좋아요 → duplicateLike(409)")
    void 중복_좋아요() {
      postLikeService.like(bob.getId(), post.getId());

      assertThatThrownBy(() -> postLikeService.like(bob.getId(), post.getId()))
          .isInstanceOf(PostLikeException.class)
          .satisfies(
              e ->
                  assertThat(((PostLikeException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
    }

    @Test
    @DisplayName("취소 후 재좋아요 → restore (기존 행 복원)")
    void 재좋아요() {
      PostLike first = postLikeService.like(bob.getId(), post.getId());
      Long firstId = first.getId();
      postLikeService.unlike(bob.getId(), post.getId());

      PostLike restored = postLikeService.like(bob.getId(), post.getId());

      assertThat(restored.getId()).isEqualTo(firstId);
      assertThat(restored.isCancelled()).isFalse();
      assertThat(postService.getById(post.getId()).getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("userId null → invalidField")
    void userId_null() {
      assertThatThrownBy(() -> postLikeService.like(null, post.getId()))
          .isInstanceOf(PostLikeException.class);
    }

    @Test
    @DisplayName("postId null → invalidField")
    void postId_null() {
      assertThatThrownBy(() -> postLikeService.like(bob.getId(), null))
          .isInstanceOf(PostLikeException.class);
    }
  }

  @Nested
  @DisplayName("unlike()")
  class Unlike {

    @Test
    @DisplayName("좋아요 취소 + Post.likeCount 감소")
    void 취소_성공() {
      postLikeService.like(bob.getId(), post.getId());
      assertThat(postService.getById(post.getId()).getLikeCount()).isEqualTo(1);

      postLikeService.unlike(bob.getId(), post.getId());

      assertThat(postLikeService.isLiked(bob.getId(), post.getId())).isFalse();
      assertThat(postService.getById(post.getId()).getLikeCount()).isZero();
    }

    @Test
    @DisplayName("좋아요 없음 → notLiked(400)")
    void 좋아요_없음() {
      assertThatThrownBy(() -> postLikeService.unlike(bob.getId(), post.getId()))
          .isInstanceOf(PostLikeException.class)
          .hasMessageContaining("좋아요하지 않은");
    }
  }

  @Nested
  @DisplayName("isLiked()")
  class IsLiked {

    @Test
    @DisplayName("활성 좋아요 → true")
    void 활성() {
      postLikeService.like(bob.getId(), post.getId());
      assertThat(postLikeService.isLiked(bob.getId(), post.getId())).isTrue();
    }

    @Test
    @DisplayName("취소됨 → false")
    void 취소됨() {
      postLikeService.like(bob.getId(), post.getId());
      postLikeService.unlike(bob.getId(), post.getId());
      assertThat(postLikeService.isLiked(bob.getId(), post.getId())).isFalse();
    }

    @Test
    @DisplayName("미존재 → false")
    void 미존재() {
      assertThat(postLikeService.isLiked(bob.getId(), post.getId())).isFalse();
    }
  }

  @Nested
  @DisplayName("getLikes()")
  class GetLikes {

    @Test
    @DisplayName("게시글 좋아요 목록 — 취소 제외")
    void 목록_취소_제외() {
      postLikeService.like(alice.getId(), post.getId());
      postLikeService.like(bob.getId(), post.getId());
      postLikeService.unlike(alice.getId(), post.getId());

      Page<PostLike> likes = postLikeService.getLikes(post.getId(), PageRequest.of(0, 10));

      assertThat(likes.getContent()).hasSize(1);
      assertThat(likes.getContent().get(0).getUserId()).isEqualTo(bob.getId());
    }
  }

  @Nested
  @DisplayName("getLikedPostIds()")
  class GetLikedPostIds {

    @Test
    @DisplayName("일괄 조회 — N+1 방지")
    void 일괄_조회() {
      Post post2 = postService.create(alice.getId(), "post2", null, null, null);
      Post post3 = postService.create(alice.getId(), "post3", null, null, null);

      postLikeService.like(bob.getId(), post.getId());
      postLikeService.like(bob.getId(), post3.getId());

      Set<Long> likedIds =
          postLikeService.getLikedPostIds(
              bob.getId(), Set.of(post.getId(), post2.getId(), post3.getId()));

      assertThat(likedIds).containsExactlyInAnyOrder(post.getId(), post3.getId());
    }

    @Test
    @DisplayName("빈 목록 → empty set")
    void 빈_목록() {
      Set<Long> likedIds = postLikeService.getLikedPostIds(bob.getId(), Set.of());
      assertThat(likedIds).isEmpty();
    }

    @Test
    @DisplayName("null → empty set")
    void null_목록() {
      Set<Long> likedIds = postLikeService.getLikedPostIds(bob.getId(), null);
      assertThat(likedIds).isEmpty();
    }
  }
}
