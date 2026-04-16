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

  @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id")
  Optional<Post> findWithAuthorById(Long id);

  @Query(
      value = "SELECT p FROM Post p JOIN FETCH p.author",
      countQuery = "SELECT COUNT(p) FROM Post p")
  Page<Post> findAllWithAuthor(Pageable pageable);

  /** 사용자 삭제 시 해당 사용자의 모든 게시글 물리 삭제. */
  @Modifying
  @Query("DELETE FROM Post p WHERE p.author = :author")
  void deleteAllByAuthor(@Param("author") User author);
}
