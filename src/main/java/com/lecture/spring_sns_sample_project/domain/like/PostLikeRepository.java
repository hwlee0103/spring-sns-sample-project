package com.lecture.spring_sns_sample_project.domain.like;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

  /** 취소된 좋아요 포함 전체 조회 — 복원용. */
  Optional<PostLike> findByUserIdAndPostId(Long userId, Long postId);

  /** 활성 좋아요 조회 (soft delete 제외). */
  @Query(
      "SELECT pl FROM PostLike pl"
          + " WHERE pl.userId = :userId AND pl.postId = :postId AND pl.deletedAt IS NULL")
  Optional<PostLike> findActiveByUserIdAndPostId(
      @Param("userId") Long userId, @Param("postId") Long postId);

  /** 활성 좋아요 존재 확인. */
  @Query(
      "SELECT CASE WHEN COUNT(pl) > 0 THEN true ELSE false END FROM PostLike pl"
          + " WHERE pl.userId = :userId AND pl.postId = :postId AND pl.deletedAt IS NULL")
  boolean existsActiveByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

  /** 게시글의 좋아요 목록 — 최신순 페이징. */
  @Query(
      value = "SELECT pl FROM PostLike pl WHERE pl.postId = :postId AND pl.deletedAt IS NULL",
      countQuery =
          "SELECT COUNT(pl) FROM PostLike pl WHERE pl.postId = :postId AND pl.deletedAt IS NULL")
  Page<PostLike> findActiveByPostId(@Param("postId") Long postId, Pageable pageable);

  /** 피드 렌더링용 — 여러 게시글에 대한 내 좋아요 상태 일괄 조회 (N+1 방지). */
  @Query(
      "SELECT pl.postId FROM PostLike pl"
          + " WHERE pl.userId = :userId AND pl.postId IN :postIds AND pl.deletedAt IS NULL")
  Set<Long> findLikedPostIdsByUserIdAndPostIdIn(
      @Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
