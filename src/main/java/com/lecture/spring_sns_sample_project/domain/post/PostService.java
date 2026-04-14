package com.lecture.spring_sns_sample_project.domain.post;

import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
