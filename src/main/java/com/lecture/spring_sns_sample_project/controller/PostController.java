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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시글 CRUD + 피드 컨트롤러.
 *
 * <p>통합 생성 엔드포인트 {@code POST /api/v1/post} 에서 request body 의 parentId/quoteId/repostId 조합에 따라
 * 일반/답글/인용/리포스트를 분기한다.
 */
@RestController
@RequiredArgsConstructor
public class PostController {

  private final PostService postService;

  /** 게시글 생성 — 통합 (일반/답글/인용/리포스트). */
  @PostMapping("/api/v1/post")
  public ResponseEntity<PostResponse> create(
      @Valid @RequestBody PostCreateRequest request, @AuthenticationPrincipal AuthUser authUser) {
    requireAuth(authUser);
    Post post =
        postService.create(
            authUser.getId(),
            request.content(),
            request.parentId(),
            request.quoteId(),
            request.repostId());
    return ResponseEntity.created(URI.create("/api/v1/post/" + post.getId()))
        .body(PostResponse.from(post));
  }

  /** 단건 조회 — 삭제된 게시글도 반환 (스레드 표시용). */
  @GetMapping("/api/v1/post/{id}")
  public ResponseEntity<PostResponse> getPost(@PathVariable Long id) {
    return ResponseEntity.ok(PostResponse.from(postService.getById(id)));
  }

  /** 전체 피드 — 삭제되지 않은 게시글만, 최신순. */
  @GetMapping("/api/v1/post")
  public ResponseEntity<PageResponse<PostResponse>> getFeed(
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(PageResponse.from(postService.getFeed(pageable), PostResponse::from));
  }

  /** 답글 목록 (스레드). */
  @GetMapping("/api/v1/post/{id}/replies")
  public ResponseEntity<PageResponse<PostResponse>> getReplies(
      @PathVariable Long id,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
          Pageable pageable) {
    return ResponseEntity.ok(
        PageResponse.from(postService.getReplies(id, pageable), PostResponse::from));
  }

  /** 인용 목록. */
  @GetMapping("/api/v1/post/{id}/quotes")
  public ResponseEntity<PageResponse<PostResponse>> getQuotes(
      @PathVariable Long id,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        PageResponse.from(postService.getQuotes(id, pageable), PostResponse::from));
  }

  /** 사용자의 게시글 목록 — 프로필 피드. */
  @GetMapping("/api/v1/user/{userId}/posts")
  public ResponseEntity<PageResponse<PostResponse>> getUserPosts(
      @PathVariable Long userId,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        PageResponse.from(postService.getUserPosts(userId, pageable), PostResponse::from));
  }

  /** 게시글 수정 — 20분 윈도우. */
  @PutMapping("/api/v1/post/{id}")
  public ResponseEntity<PostResponse> update(
      @PathVariable Long id,
      @Valid @RequestBody PostUpdateRequest request,
      @AuthenticationPrincipal AuthUser authUser) {
    requireAuth(authUser);
    Post post = postService.update(authUser.getId(), id, request.content());
    return ResponseEntity.ok(PostResponse.from(post));
  }

  /** 게시글 삭제 — soft delete. */
  @DeleteMapping("/api/v1/post/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable Long id, @AuthenticationPrincipal AuthUser authUser) {
    requireAuth(authUser);
    postService.delete(authUser.getId(), id);
    return ResponseEntity.noContent().build();
  }

  private static void requireAuth(AuthUser authUser) {
    if (authUser == null) {
      throw new AccessDeniedException("인증이 필요합니다.");
    }
  }
}
