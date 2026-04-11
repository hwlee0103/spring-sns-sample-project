package com.lecture.spring_sns_sample_project.domain.post;

import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

  private final PostRepository postRepository;
  private final UserService userService;

  public Post create(String authorEmail, String content) {
    User author = userService.getByEmail(authorEmail);
    Post post = new Post(author, content);
    return postRepository.save(post);
  }

  public Page<Post> getFeed(Pageable pageable) {
    return postRepository.findAllByOrderByIdDesc(pageable);
  }

  public Post getById(Long id) {
    return postRepository.findById(id).orElseThrow(() -> PostException.notFound(id));
  }

  @Transactional
  public Post update(String requesterEmail, Long id, String content) {
    Post post = getById(id);
    User requester = userService.getByEmail(requesterEmail);
    if (!post.isAuthor(requester.getId())) {
      throw PostException.forbidden(id);
    }
    post.updateContent(content);
    return post;
  }

  public void delete(String requesterEmail, Long id) {
    Post post = getById(id);
    User requester = userService.getByEmail(requesterEmail);
    if (!post.isAuthor(requester.getId())) {
      throw PostException.forbidden(id);
    }
    postRepository.delete(post);
  }
}
