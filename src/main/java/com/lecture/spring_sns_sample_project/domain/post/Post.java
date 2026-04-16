package com.lecture.spring_sns_sample_project.domain.post;

import com.lecture.spring_sns_sample_project.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(
    name = "posts",
    indexes = {@Index(name = "idx_posts_author_id", columnList = "author_id")})
@Getter
public class Post {

  public static final int MAX_CONTENT_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "author_id",
      nullable = false,
      foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
  private User author;

  @Column(nullable = false, length = MAX_CONTENT_LENGTH)
  private String content;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected Post() {}

  public Post(User author, String content) {
    if (author == null) {
      throw PostException.invalidField("author");
    }
    if (content == null || content.isBlank()) {
      throw PostException.invalidField("content");
    }
    if (content.length() > MAX_CONTENT_LENGTH) {
      throw PostException.contentTooLong();
    }
    this.author = author;
    this.content = content;
    this.createdAt = Instant.now();
  }

  public void updateContent(String content) {
    if (content == null || content.isBlank()) {
      throw PostException.invalidField("content");
    }
    if (content.length() > MAX_CONTENT_LENGTH) {
      throw PostException.contentTooLong();
    }
    this.content = content;
  }

  public boolean isAuthor(Long userId) {
    return author != null && author.getId() != null && author.getId().equals(userId);
  }
}
