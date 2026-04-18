package com.lecture.spring_sns_sample_project.domain.post;

import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 도메인 서비스.
 *
 * <p>통합 생성 메서드 {@link #create} 에서 parentId/quoteId/repostId 조합에 따라 유형별 분기하여 검증·생성·카운트 갱신을 단일 트랜잭션으로
 * 처리한다.
 */
@Service
@RequiredArgsConstructor
public class PostService {

  private final PostRepository postRepository;
  private final UserRepository userRepository;
  private final ViewCountRecorder viewCountRecorder;

  /**
   * 게시글 생성 — 유형별 분기.
   *
   * <p>분기 우선순위: repostId > quoteId > parentId > ORIGINAL.
   *
   * <ul>
   *   <li>REPOST: content 금지, 원본 존재+미삭제+미리포스트+미중복 검증
   *   <li>QUOTE: content 필수, 인용 대상 존재+미중복 검증, 원본 repostCount++
   *   <li>REPLY: content 필수, 부모 존재+미삭제 검증, 부모 replyCount++
   *   <li>ORIGINAL: content 필수
   * </ul>
   */
  @Transactional
  public Post create(Long authorId, String content, Long parentId, Long quoteId, Long repostId) {
    if (authorId == null) {
      throw PostException.invalidField("authorId");
    }
    User author =
        userRepository.findById(authorId).orElseThrow(() -> PostException.authorNotFound(authorId));

    Post post;
    if (repostId != null) {
      validateRepostTarget(repostId, authorId);
      post = Post.repost(author, repostId);
      postRepository.save(post);
      postRepository.incrementRepostCount(repostId);
    } else if (quoteId != null && parentId != null) {
      validateQuoteTarget(quoteId, authorId);
      validateReplyTarget(parentId);
      post = Post.replyWithQuote(author, content, parentId, quoteId);
      postRepository.save(post);
      postRepository.incrementRepostCount(quoteId);
      postRepository.incrementReplyCount(parentId);
    } else if (quoteId != null) {
      validateQuoteTarget(quoteId, authorId);
      post = Post.quote(author, content, quoteId);
      postRepository.save(post);
      postRepository.incrementRepostCount(quoteId);
    } else if (parentId != null) {
      validateReplyTarget(parentId);
      post = Post.reply(author, content, parentId);
      postRepository.save(post);
      postRepository.incrementReplyCount(parentId);
    } else {
      if (content == null || content.isBlank()) {
        throw PostException.invalidField("content");
      }
      post = new Post(author, content);
      postRepository.save(post);
    }

    return post;
  }

  /** 단건 조회 (공개 API 용). 삭제된 게시글도 반환 (스레드 표시). 조회수는 Redis Dirty Set 패턴으로 기록. */
  public Post getById(Long id) {
    Post post = findById(id);
    if (!post.isDeleted()) {
      viewCountRecorder.increment(id);
    }
    return post;
  }

  /** 내부 조회 — 조회수를 올리지 않는다. update/delete/검증 용도. */
  private Post findById(Long id) {
    return postRepository.findWithAuthorById(id).orElseThrow(() -> PostException.notFound(id));
  }

  /** 전체 피드 — 삭제되지 않은 게시글만. */
  public Page<Post> getFeed(Pageable pageable) {
    return postRepository.findAllWithAuthor(pageable);
  }

  /** 답글 목록 (스레드). */
  public Page<Post> getReplies(Long postId, Pageable pageable) {
    return postRepository.findRepliesByParentId(postId, pageable);
  }

  /** 인용 목록. */
  public Page<Post> getQuotes(Long postId, Pageable pageable) {
    return postRepository.findQuotesByQuoteId(postId, pageable);
  }

  /** 사용자의 게시글 목록. */
  public Page<Post> getUserPosts(Long userId, Pageable pageable) {
    return postRepository.findByAuthorIdWithAuthor(userId, pageable);
  }

  /**
   * 수정 — 20분 윈도우.
   *
   * <p>parentId/quoteId/repostId 는 변경 불가. content 만 수정 대상.
   */
  @Transactional
  public Post update(Long requesterId, Long id, String content) {
    if (requesterId == null) {
      throw PostException.invalidField("requesterId");
    }
    if (content == null || content.isBlank()) {
      throw PostException.invalidField("content");
    }
    Post post = findById(id);
    if (!post.isAuthor(requesterId)) {
      throw PostException.forbidden(id);
    }
    post.updateContent(content);
    return post;
  }

  /**
   * 삭제 — soft delete.
   *
   * <p>답글 삭제 시 부모 replyCount--, 리포스트/인용 삭제 시 원본 repostCount--.
   */
  @Transactional
  public void delete(Long requesterId, Long id) {
    if (requesterId == null) {
      throw PostException.invalidField("requesterId");
    }
    Post post = findById(id);
    if (!post.isAuthor(requesterId)) {
      throw PostException.forbidden(id);
    }
    post.softDelete();

    if (post.getParentId() != null) {
      postRepository.decrementReplyCount(post.getParentId());
    }
    if (post.getRepostId() != null) {
      postRepository.decrementRepostCount(post.getRepostId());
    }
    if (post.getQuoteId() != null) {
      postRepository.decrementRepostCount(post.getQuoteId());
    }
  }

  // --- 검증 메서드 ---

  private void validateReplyTarget(Long parentId) {
    Post parent =
        postRepository
            .findWithAuthorById(parentId)
            .orElseThrow(() -> PostException.notFound(parentId));
    if (parent.isDeleted()) {
      throw PostException.replyToDeletedPost();
    }
  }

  private void validateQuoteTarget(Long quoteId, Long authorId) {
    Post target = findById(quoteId);
    if (target.isDeleted()) {
      throw PostException.notFound(quoteId);
    }
    if (postRepository.existsByQuoteIdAndAuthorId(quoteId, authorId)) {
      throw PostException.duplicateQuote();
    }
  }

  private void validateRepostTarget(Long repostId, Long authorId) {
    Post original =
        postRepository
            .findWithAuthorById(repostId)
            .orElseThrow(() -> PostException.notFound(repostId));
    if (original.isDeleted()) {
      throw PostException.repostDeletedPost();
    }
    if (original.getRepostId() != null) {
      throw PostException.repostOfRepost();
    }
    if (postRepository.existsByRepostIdAndAuthorId(repostId, authorId)) {
      throw PostException.duplicateRepost();
    }
  }
}
