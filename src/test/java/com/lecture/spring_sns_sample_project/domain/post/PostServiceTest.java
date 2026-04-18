package com.lecture.spring_sns_sample_project.domain.post;

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

/**
 * PostService 통합 테스트.
 *
 * <p>4종 게시글(ORIGINAL/REPLY/QUOTE/REPOST) 생성·수정(20분 윈도우)·삭제(soft delete + 카운트) + 조회를 실제 JPA 로 검증.
 */
@SpringBootTest
@Import(TestSessionConfig.class)
@DisplayName("PostService 통합 테스트")
class PostServiceTest {

  @Autowired private PostService postService;
  @Autowired private PostRepository postRepository;
  @Autowired private UserRepository userRepository;

  private User alice;
  private User bob;

  @BeforeEach
  void setUp() {
    postRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();

    alice = userRepository.saveAndFlush(new User("alice@example.com", "encoded-alice", "alice"));
    bob = userRepository.saveAndFlush(new User("bob@example.com", "encoded-bob", "bob"));
  }

  @Nested
  @DisplayName("create — ORIGINAL")
  class CreateOriginal {

    @Test
    @DisplayName("일반 게시글 생성 + 카운트 초기값 0")
    void 정상_생성() {
      Post post = postService.create(alice.getId(), "Hello world", null, null, null);

      assertThat(post.getId()).isNotNull();
      assertThat(post.getContent()).isEqualTo("Hello world");
      assertThat(post.getType()).isEqualTo(PostType.ORIGINAL);
      assertThat(post.getParentId()).isNull();
      assertThat(post.getReplyCount()).isZero();
      assertThat(post.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("content null → invalidField")
    void content_null() {
      assertThatThrownBy(() -> postService.create(alice.getId(), null, null, null, null))
          .isInstanceOf(PostException.class);
    }

    @Test
    @DisplayName("content blank → invalidField")
    void content_blank() {
      assertThatThrownBy(() -> postService.create(alice.getId(), "  ", null, null, null))
          .isInstanceOf(PostException.class);
    }

    @Test
    @DisplayName("content 1000자 초과 → contentTooLong")
    void content_too_long() {
      String longContent = "a".repeat(1001);
      assertThatThrownBy(() -> postService.create(alice.getId(), longContent, null, null, null))
          .isInstanceOf(PostException.class)
          .hasMessageContaining("1000");
    }

    @Test
    @DisplayName("authorId null → invalidField")
    void authorId_null() {
      assertThatThrownBy(() -> postService.create(null, "test", null, null, null))
          .isInstanceOf(PostException.class);
    }

    @Test
    @DisplayName("author 미존재 → authorNotFound")
    void author_미존재() {
      assertThatThrownBy(() -> postService.create(99_999L, "test", null, null, null))
          .isInstanceOf(PostException.class)
          .satisfies(
              e -> assertThat(((PostException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }
  }

  @Nested
  @DisplayName("create — REPLY")
  class CreateReply {

    @Test
    @DisplayName("답글 생성 + 부모 replyCount 증가")
    void 답글_성공() {
      Post parent = postService.create(alice.getId(), "parent", null, null, null);

      Post reply = postService.create(bob.getId(), "reply!", parent.getId(), null, null);

      assertThat(reply.getType()).isEqualTo(PostType.REPLY);
      assertThat(reply.getParentId()).isEqualTo(parent.getId());

      Post updatedParent = postService.getById(parent.getId());
      assertThat(updatedParent.getReplyCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("부모 미존재 → notFound")
    void 부모_미존재() {
      assertThatThrownBy(() -> postService.create(alice.getId(), "reply", 99_999L, null, null))
          .isInstanceOf(PostException.class)
          .satisfies(
              e -> assertThat(((PostException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 부모에 답글 → replyToDeletedPost")
    void 삭제된_부모() {
      Post parent = postService.create(alice.getId(), "parent", null, null, null);
      postService.delete(alice.getId(), parent.getId());

      assertThatThrownBy(() -> postService.create(bob.getId(), "reply", parent.getId(), null, null))
          .isInstanceOf(PostException.class)
          .hasMessageContaining("삭제된");
    }
  }

  @Nested
  @DisplayName("create — QUOTE")
  class CreateQuote {

    @Test
    @DisplayName("인용 생성 + 원본 repostCount 증가")
    void 인용_성공() {
      Post original = postService.create(alice.getId(), "original", null, null, null);

      Post quote = postService.create(bob.getId(), "quoting this", null, original.getId(), null);

      assertThat(quote.getType()).isEqualTo(PostType.QUOTE);
      assertThat(quote.getQuoteId()).isEqualTo(original.getId());

      Post updatedOriginal = postService.getById(original.getId());
      assertThat(updatedOriginal.getRepostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("중복 인용 → duplicateQuote(409)")
    void 중복_인용() {
      Post original = postService.create(alice.getId(), "original", null, null, null);
      postService.create(bob.getId(), "first quote", null, original.getId(), null);

      assertThatThrownBy(
              () -> postService.create(bob.getId(), "second quote", null, original.getId(), null))
          .isInstanceOf(PostException.class)
          .satisfies(
              e -> assertThat(((PostException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
    }

    @Test
    @DisplayName("삭제된 게시글 인용 → notFound")
    void 삭제된_게시글_인용() {
      Post original = postService.create(alice.getId(), "original", null, null, null);
      postService.delete(alice.getId(), original.getId());

      assertThatThrownBy(
              () -> postService.create(bob.getId(), "quote", null, original.getId(), null))
          .isInstanceOf(PostException.class);
    }
  }

  @Nested
  @DisplayName("create — REPOST")
  class CreateRepost {

    @Test
    @DisplayName("리포스트 생성(content=null) + 원본 repostCount 증가")
    void 리포스트_성공() {
      Post original = postService.create(alice.getId(), "original", null, null, null);

      Post repost = postService.create(bob.getId(), null, null, null, original.getId());

      assertThat(repost.getType()).isEqualTo(PostType.REPOST);
      assertThat(repost.getContent()).isNull();
      assertThat(repost.getRepostId()).isEqualTo(original.getId());

      Post updatedOriginal = postService.getById(original.getId());
      assertThat(updatedOriginal.getRepostCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("삭제된 게시글 리포스트 → repostDeletedPost")
    void 삭제된_원본() {
      Post original = postService.create(alice.getId(), "original", null, null, null);
      postService.delete(alice.getId(), original.getId());

      assertThatThrownBy(() -> postService.create(bob.getId(), null, null, null, original.getId()))
          .isInstanceOf(PostException.class)
          .hasMessageContaining("삭제된");
    }

    @Test
    @DisplayName("리포스트의 리포스트 → repostOfRepost")
    void 리포스트의_리포스트() {
      Post original = postService.create(alice.getId(), "original", null, null, null);
      Post repost = postService.create(bob.getId(), null, null, null, original.getId());

      assertThatThrownBy(() -> postService.create(alice.getId(), null, null, null, repost.getId()))
          .isInstanceOf(PostException.class)
          .hasMessageContaining("원본");
    }

    @Test
    @DisplayName("중복 리포스트 → duplicateRepost(409)")
    void 중복_리포스트() {
      Post original = postService.create(alice.getId(), "original", null, null, null);
      postService.create(bob.getId(), null, null, null, original.getId());

      assertThatThrownBy(() -> postService.create(bob.getId(), null, null, null, original.getId()))
          .isInstanceOf(PostException.class)
          .satisfies(
              e -> assertThat(((PostException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
    }
  }

  @Nested
  @DisplayName("update — 20분 윈도우")
  class Update {

    @Test
    @DisplayName("20분 내 수정 성공")
    void 정상_수정() {
      Post post = postService.create(alice.getId(), "original", null, null, null);

      Post updated = postService.update(alice.getId(), post.getId(), "updated");

      assertThat(updated.getContent()).isEqualTo("updated");
    }

    @Test
    @DisplayName("타인 게시글 수정 → forbidden")
    void 타인_게시글() {
      Post post = postService.create(alice.getId(), "original", null, null, null);

      assertThatThrownBy(() -> postService.update(bob.getId(), post.getId(), "hacked"))
          .isInstanceOf(PostException.class)
          .satisfies(
              e -> assertThat(((PostException) e).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
    }

    @Test
    @DisplayName("리포스트 수정 불가 → editWindowExpired")
    void 리포스트_수정_불가() {
      Post original = postService.create(alice.getId(), "original", null, null, null);
      Post repost = postService.create(bob.getId(), null, null, null, original.getId());

      assertThatThrownBy(() -> postService.update(bob.getId(), repost.getId(), "content"))
          .isInstanceOf(PostException.class)
          .hasMessageContaining("수정");
    }
  }

  @Nested
  @DisplayName("delete — soft delete + 카운트")
  class Delete {

    @Test
    @DisplayName("soft delete — content null + deletedAt 설정")
    void 정상_삭제() {
      Post post = postService.create(alice.getId(), "to be deleted", null, null, null);

      postService.delete(alice.getId(), post.getId());

      Post deleted = postService.getById(post.getId());
      assertThat(deleted.isDeleted()).isTrue();
      assertThat(deleted.getContent()).isNull();
    }

    @Test
    @DisplayName("답글 삭제 → 부모 replyCount 감소")
    void 답글_삭제_카운트() {
      Post parent = postService.create(alice.getId(), "parent", null, null, null);
      Post reply = postService.create(bob.getId(), "reply", parent.getId(), null, null);

      postService.delete(bob.getId(), reply.getId());

      Post updatedParent = postService.getById(parent.getId());
      assertThat(updatedParent.getReplyCount()).isZero();
    }

    @Test
    @DisplayName("리포스트 삭제 → 원본 repostCount 감소")
    void 리포스트_삭제_카운트() {
      Post original = postService.create(alice.getId(), "original", null, null, null);
      Post repost = postService.create(bob.getId(), null, null, null, original.getId());

      postService.delete(bob.getId(), repost.getId());

      Post updatedOriginal = postService.getById(original.getId());
      assertThat(updatedOriginal.getRepostCount()).isZero();
    }

    @Test
    @DisplayName("인용 삭제 → 원본 repostCount 감소")
    void 인용_삭제_카운트() {
      Post original = postService.create(alice.getId(), "original", null, null, null);
      Post quote = postService.create(bob.getId(), "quoting", null, original.getId(), null);

      postService.delete(bob.getId(), quote.getId());

      Post updatedOriginal = postService.getById(original.getId());
      assertThat(updatedOriginal.getRepostCount()).isZero();
    }

    @Test
    @DisplayName("타인 게시글 삭제 → forbidden")
    void 타인_삭제() {
      Post post = postService.create(alice.getId(), "my post", null, null, null);

      assertThatThrownBy(() -> postService.delete(bob.getId(), post.getId()))
          .isInstanceOf(PostException.class)
          .satisfies(
              e -> assertThat(((PostException) e).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
    }
  }

  @Nested
  @DisplayName("조회")
  class Query {

    @Test
    @DisplayName("getById — 삭제된 게시글도 반환")
    void 삭제된_게시글_반환() {
      Post post = postService.create(alice.getId(), "hello", null, null, null);
      postService.delete(alice.getId(), post.getId());

      Post found = postService.getById(post.getId());

      assertThat(found.isDeleted()).isTrue();
      assertThat(found.getContent()).isNull();
    }

    @Test
    @DisplayName("getFeed — 삭제된 게시글 제외")
    void 피드_삭제_제외() {
      postService.create(alice.getId(), "visible", null, null, null);
      Post deleted = postService.create(bob.getId(), "deleted", null, null, null);
      postService.delete(bob.getId(), deleted.getId());

      Page<Post> feed = postService.getFeed(PageRequest.of(0, 10));

      assertThat(feed.getContent()).hasSize(1);
      assertThat(feed.getContent().get(0).getContent()).isEqualTo("visible");
    }

    @Test
    @DisplayName("getReplies — 답글 목록 페이징")
    void 답글_목록() {
      Post parent = postService.create(alice.getId(), "parent", null, null, null);
      postService.create(bob.getId(), "reply1", parent.getId(), null, null);
      postService.create(alice.getId(), "reply2", parent.getId(), null, null);

      Page<Post> replies = postService.getReplies(parent.getId(), PageRequest.of(0, 10));

      assertThat(replies.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("getUserPosts — 사용자 게시글")
    void 사용자_게시글() {
      postService.create(alice.getId(), "alice1", null, null, null);
      postService.create(alice.getId(), "alice2", null, null, null);
      postService.create(bob.getId(), "bob1", null, null, null);

      Page<Post> alicePosts = postService.getUserPosts(alice.getId(), PageRequest.of(0, 10));

      assertThat(alicePosts.getContent()).hasSize(2);
    }
  }
}
