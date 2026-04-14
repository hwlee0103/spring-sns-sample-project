package com.lecture.spring_sns_sample_project.controller;

import com.lecture.spring_sns_sample_project.controller.dto.PageResponse;
import com.lecture.spring_sns_sample_project.controller.dto.PostCreateRequest;
import com.lecture.spring_sns_sample_project.controller.dto.PostResponse;
import com.lecture.spring_sns_sample_project.controller.dto.PostUpdateRequest;
import com.lecture.spring_sns_sample_project.domain.post.Post;
import com.lecture.spring_sns_sample_project.domain.post.PostService;
import com.lecture.spring_sns_sample_project.domain.user.security.AuthUser;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PostController {

  private final PostService postService;

  @PostMapping("/api/post")
  public ResponseEntity<PostResponse> create(
      @Valid @RequestBody PostCreateRequest request, @AuthenticationPrincipal AuthUser authUser) {
    requireAuth(authUser);
    Post post = postService.create(authUser.getId(), request.content());
    return ResponseEntity.created(URI.create("/api/post/" + post.getId()))
        .body(PostResponse.from(post));
  }

  @GetMapping("/api/post/{id}")
  public ResponseEntity<PostResponse> getPost(@PathVariable Long id) {
    return ResponseEntity.ok(PostResponse.from(postService.getById(id)));
  }

  @GetMapping("/api/post")
  public ResponseEntity<PageResponse<PostResponse>> getFeed(
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    PageResponse<PostResponse> response =
        PageResponse.from(postService.getFeed(pageable), PostResponse::from);
    return ResponseEntity.ok(response);
  }

  @PutMapping("/api/post/{id}")
  public ResponseEntity<PostResponse> update(
      @PathVariable Long id,
      @Valid @RequestBody PostUpdateRequest request,
      @AuthenticationPrincipal AuthUser authUser) {
    requireAuth(authUser);
    Post post = postService.update(authUser.getId(), id, request.content());
    return ResponseEntity.ok(PostResponse.from(post));
  }

  @DeleteMapping("/api/post/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
    requireAuth(authUser);
    postService.delete(authUser.getId(), id);
    return ResponseEntity.noContent().build();
  }

  private static void requireAuth(AuthUser authUser) {
    if (authUser == null) {
      throw new org.springframework.security.access.AccessDeniedException("인증이 필요합니다.");
    }
  }
}
