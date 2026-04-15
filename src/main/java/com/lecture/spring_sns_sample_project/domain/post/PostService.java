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
 * <p>{@link UserRepository} 에 대한 크로스 도메인 의존이 존재한다. 현재 규모에서는 실용적이나, 도메인이 확장되면 Application Service
 * 레이어로 분리하여 도메인 간 의존을 제거하는 것을 검토한다.
 */
@Service
@RequiredArgsConstructor
public class PostService {

  private final PostRepository postRepository;
  private final UserRepository userRepository;

  public Post create(Long authorId, String content) {
    if (authorId == null) {
      throw PostException.invalidField("authorId");
    }
    if (content == null || content.isBlank()) {
      throw PostException.invalidField("content");
    }
    User author =
        userRepository.findById(authorId).orElseThrow(() -> PostException.authorNotFound(authorId));
    Post post = new Post(author, content);
    return postRepository.save(post);
  }

  public Page<Post> getFeed(Pageable pageable) {
    return postRepository.findAllWithAuthor(pageable);
  }

  public Post getById(Long id) {
    return postRepository.findWithAuthorById(id).orElseThrow(() -> PostException.notFound(id));
  }

  @Transactional
  public Post update(Long requesterId, Long id, String content) {
    if (requesterId == null) {
      throw PostException.invalidField("requesterId");
    }
    if (content == null || content.isBlank()) {
      throw PostException.invalidField("content");
    }
    Post post = getById(id);
    if (!post.isAuthor(requesterId)) {
      throw PostException.forbidden(id);
    }
    post.updateContent(content);
    return post;
  }

  @Transactional
  public void delete(Long requesterId, Long id) {
    if (requesterId == null) {
      throw PostException.invalidField("requesterId");
    }
    Post post = getById(id);
    if (!post.isAuthor(requesterId)) {
      throw PostException.forbidden(id);
    }
    postRepository.delete(post);
  }
}
