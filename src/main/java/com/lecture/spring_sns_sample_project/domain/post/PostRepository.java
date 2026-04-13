package com.lecture.spring_sns_sample_project.domain.post;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

  @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id")
  Optional<Post> findWithAuthorById(Long id);

  @Query(
      value = "SELECT p FROM Post p JOIN FETCH p.author",
      countQuery = "SELECT COUNT(p) FROM Post p")
  Page<Post> findAllWithAuthor(Pageable pageable);
}
