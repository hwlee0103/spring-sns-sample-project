package com.lecture.spring_sns_sample_project.domain.like;

import com.lecture.spring_sns_sample_project.domain.post.PostException;
import com.lecture.spring_sns_sample_project.domain.post.PostRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 도메인 서비스.
 *
 * <p>좋아요 생성/취소와 Post.likeCount 원자적 갱신을 단일 트랜잭션으로 처리한다. Follow 도메인의 soft delete + restore 패턴과 동일.
 */
@Service
@RequiredArgsConstructor
public class PostLikeService {

  private final PostLikeRepository postLikeRepository;
  private final PostRepository postRepository;

  /**
   * 좋아요.
   *
   * <pre>
   * 1. 게시글 존재 확인
   * 2. 기존 좋아요 조회 (soft deleted 포함)
   *    - 활성 → duplicateLike (409)
   *    - 취소됨 → restore (재좋아요)
   *    - 없음 → 새 PostLike INSERT
   * 3. Post.likeCount 원자적 증가
   * </pre>
   */
  @Transactional
  public PostLike like(Long userId, Long postId) {
    if (userId == null) {
      throw PostLikeException.invalidField("userId");
    }
    if (postId == null) {
      throw PostLikeException.invalidField("postId");
    }
    validatePostExists(postId);

    Optional<PostLike> existing = postLikeRepository.findByUserIdAndPostId(userId, postId);

    PostLike postLike;
    if (existing.isPresent()) {
      PostLike found = existing.get();
      if (!found.isCancelled()) {
        throw PostLikeException.duplicateLike();
      }
      found.restore(ReactionType.LIKE);
      postLike = found;
    } else {
      try {
        postLike = postLikeRepository.save(new PostLike(userId, postId));
      } catch (DataIntegrityViolationException e) {
        throw PostLikeException.duplicateLike();
      }
    }

    postRepository.incrementLikeCount(postId);
    return postLike;
  }

  /**
   * 좋아요 취소 — soft delete + likeCount 감소.
   *
   * <pre>
   * 1. 활성 좋아요 조회 → 없으면 notLiked (400)
   * 2. cancel (soft delete)
   * 3. Post.likeCount 원자적 감소
   * </pre>
   */
  @Transactional
  public void unlike(Long userId, Long postId) {
    if (userId == null) {
      throw PostLikeException.invalidField("userId");
    }
    if (postId == null) {
      throw PostLikeException.invalidField("postId");
    }
    PostLike postLike =
        postLikeRepository
            .findActiveByUserIdAndPostId(userId, postId)
            .orElseThrow(PostLikeException::notLiked);
    postLike.cancel();
    postRepository.decrementLikeCount(postId);
  }

  /** 좋아요 여부 확인. */
  public boolean isLiked(Long userId, Long postId) {
    return postLikeRepository.existsActiveByUserIdAndPostId(userId, postId);
  }

  /** 게시글의 좋아요 목록 — 페이징. */
  public Page<PostLike> getLikes(Long postId, Pageable pageable) {
    return postLikeRepository.findActiveByPostId(postId, pageable);
  }

  /** 피드 렌더링용 — 여러 게시글에 대한 내 좋아요 상태 일괄 조회 (N+1 방지). */
  public Set<Long> getLikedPostIds(Long userId, Collection<Long> postIds) {
    if (postIds == null || postIds.isEmpty()) {
      return Set.of();
    }
    return postLikeRepository.findLikedPostIdsByUserIdAndPostIdIn(userId, postIds);
  }

  private void validatePostExists(Long postId) {
    if (!postRepository.existsById(postId)) {
      throw PostException.notFound(postId);
    }
  }
}
