package com.lecture.spring_sns_sample_project.controller;

import com.lecture.spring_sns_sample_project.controller.dto.LikeStatusResponse;
import com.lecture.spring_sns_sample_project.controller.dto.PageResponse;
import com.lecture.spring_sns_sample_project.controller.dto.PostLikeResponse;
import com.lecture.spring_sns_sample_project.domain.like.PostLike;
import com.lecture.spring_sns_sample_project.domain.like.PostLikeService;
import com.lecture.spring_sns_sample_project.domain.user.security.AuthUser;
import com.lecture.spring_sns_sample_project.domain.user.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PostLikeController {

  private final PostLikeService postLikeService;

  @PostMapping("/api/v1/post/{id}/like")
  public ResponseEntity<PostLikeResponse> like(
      @PathVariable Long id, @CurrentUser AuthUser authUser) {
    requireAuth(authUser);
    PostLike postLike = postLikeService.like(authUser.getId(), id);
    return ResponseEntity.status(201).body(PostLikeResponse.from(postLike));
  }

  @DeleteMapping("/api/v1/post/{id}/like")
  public ResponseEntity<Void> unlike(@PathVariable Long id, @CurrentUser AuthUser authUser) {
    requireAuth(authUser);
    postLikeService.unlike(authUser.getId(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/v1/post/{id}/like/status")
  public ResponseEntity<LikeStatusResponse> likeStatus(
      @PathVariable Long id, @CurrentUser AuthUser authUser) {
    requireAuth(authUser);
    boolean liked = postLikeService.isLiked(authUser.getId(), id);
    return ResponseEntity.ok(new LikeStatusResponse(liked));
  }

  @GetMapping("/api/v1/post/{id}/likes")
  public ResponseEntity<PageResponse<PostLikeResponse>> getLikes(
      @PathVariable Long id,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        PageResponse.from(postLikeService.getLikes(id, pageable), PostLikeResponse::from));
  }

  private static void requireAuth(AuthUser authUser) {
    if (authUser == null) {
      throw new AccessDeniedException("인증이 필요합니다.");
    }
  }
}
