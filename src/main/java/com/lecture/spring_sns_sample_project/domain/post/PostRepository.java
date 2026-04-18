package com.lecture.spring_sns_sample_project.domain.post;

import com.lecture.spring_sns_sample_project.domain.user.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

  // --- 조회 ---

  /** 단건 조회 — author fetch join, 삭제 포함 (삭제된 게시글도 스레드에서 표시). */
  @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id")
  Optional<Post> findWithAuthorById(Long id);

  /** 전체 피드 — 삭제되지 않은 게시글만, 최신순. */
  @Query(
      value = "SELECT p FROM Post p JOIN FETCH p.author WHERE p.deletedAt IS NULL",
      countQuery = "SELECT COUNT(p) FROM Post p WHERE p.deletedAt IS NULL")
  Page<Post> findAllWithAuthor(Pageable pageable);

  /** 사용자의 게시글 — 프로필 피드. */
  @Query(
      value =
          "SELECT p FROM Post p JOIN FETCH p.author WHERE p.author.id = :authorId AND p.deletedAt IS NULL",
      countQuery =
          "SELECT COUNT(p) FROM Post p WHERE p.author.id = :authorId AND p.deletedAt IS NULL")
  Page<Post> findByAuthorIdWithAuthor(@Param("authorId") Long authorId, Pageable pageable);

  /** 답글 목록 (스레드). */
  @Query(
      value =
          "SELECT p FROM Post p JOIN FETCH p.author WHERE p.parentId = :parentId AND p.deletedAt IS NULL",
      countQuery =
          "SELECT COUNT(p) FROM Post p WHERE p.parentId = :parentId AND p.deletedAt IS NULL")
  Page<Post> findRepliesByParentId(@Param("parentId") Long parentId, Pageable pageable);

  /** 인용 목록. */
  @Query(
      value =
          "SELECT p FROM Post p JOIN FETCH p.author WHERE p.quoteId = :quoteId AND p.deletedAt IS NULL",
      countQuery = "SELECT COUNT(p) FROM Post p WHERE p.quoteId = :quoteId AND p.deletedAt IS NULL")
  Page<Post> findQuotesByQuoteId(@Param("quoteId") Long quoteId, Pageable pageable);

  // --- 중복 확인 ---

  @Query(
      "SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Post p"
          + " WHERE p.quoteId = :quoteId AND p.author.id = :authorId AND p.deletedAt IS NULL")
  boolean existsByQuoteIdAndAuthorId(
      @Param("quoteId") Long quoteId, @Param("authorId") Long authorId);

  @Query(
      "SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Post p"
          + " WHERE p.repostId = :repostId AND p.author.id = :authorId AND p.deletedAt IS NULL")
  boolean existsByRepostIdAndAuthorId(
      @Param("repostId") Long repostId, @Param("authorId") Long authorId);

  // --- 카운트 원자적 갱신 ---

  @Modifying
  @Query("UPDATE Post p SET p.replyCount = p.replyCount + 1 WHERE p.id = :postId")
  int incrementReplyCount(@Param("postId") Long postId);

  @Modifying
  @Query(
      "UPDATE Post p SET p.replyCount = p.replyCount - 1 WHERE p.id = :postId AND p.replyCount > 0")
  int decrementReplyCount(@Param("postId") Long postId);

  @Modifying
  @Query("UPDATE Post p SET p.repostCount = p.repostCount + 1 WHERE p.id = :postId")
  int incrementRepostCount(@Param("postId") Long postId);

  @Modifying
  @Query(
      "UPDATE Post p SET p.repostCount = p.repostCount - 1 WHERE p.id = :postId AND p.repostCount > 0")
  int decrementRepostCount(@Param("postId") Long postId);

  @Modifying
  @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
  int incrementLikeCount(@Param("postId") Long postId);

  @Modifying
  @Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
  int decrementLikeCount(@Param("postId") Long postId);

  @Modifying
  @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :postId")
  int incrementViewCount(@Param("postId") Long postId);

  /** 조회수 배치 증가 — ViewCountSyncScheduler 에서 delta 만큼 한 번에 증가. */
  @Modifying
  @Query("UPDATE Post p SET p.viewCount = p.viewCount + :delta WHERE p.id = :postId")
  int incrementViewCountBy(@Param("postId") Long postId, @Param("delta") long delta);

  @Modifying
  @Query("UPDATE Post p SET p.shareCount = p.shareCount + 1 WHERE p.id = :postId")
  int incrementShareCount(@Param("postId") Long postId);

  // --- 사용자 삭제 cascade ---

  @Modifying
  @Query("DELETE FROM Post p WHERE p.author = :author")
  void deleteAllByAuthor(@Param("author") User author);
}
