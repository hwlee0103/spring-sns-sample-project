package com.lecture.spring_sns_sample_project.controller;

import com.lecture.spring_sns_sample_project.controller.dto.FollowCountResponse;
import com.lecture.spring_sns_sample_project.controller.dto.FollowResponse;
import com.lecture.spring_sns_sample_project.controller.dto.FollowStatusResponse;
import com.lecture.spring_sns_sample_project.controller.dto.FollowUserResponse;
import com.lecture.spring_sns_sample_project.controller.dto.PageResponse;
import com.lecture.spring_sns_sample_project.domain.follow.Follow;
import com.lecture.spring_sns_sample_project.domain.follow.FollowService;
import com.lecture.spring_sns_sample_project.domain.user.security.AuthUser;
import com.lecture.spring_sns_sample_project.domain.user.security.CurrentUser;
import java.net.URI;
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
public class FollowController {

  private final FollowService followService;

  @PostMapping("/api/v1/user/{id}/follow")
  public ResponseEntity<FollowResponse> follow(
      @PathVariable Long id, @CurrentUser AuthUser authUser) {
    requireAuth(authUser);
    Follow follow = followService.follow(authUser.getId(), id);
    return ResponseEntity.created(URI.create("/api/v1/user/" + id + "/follow"))
        .body(FollowResponse.from(follow));
  }

  @DeleteMapping("/api/v1/user/{id}/follow")
  public ResponseEntity<Void> unfollow(@PathVariable Long id, @CurrentUser AuthUser authUser) {
    requireAuth(authUser);
    followService.unfollow(authUser.getId(), id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/v1/user/{id}/followers")
  public ResponseEntity<PageResponse<FollowUserResponse>> getFollowers(
      @PathVariable Long id,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        PageResponse.from(
            followService.getFollowers(id, pageable), FollowUserResponse::fromFollower));
  }

  @GetMapping("/api/v1/user/{id}/followings")
  public ResponseEntity<PageResponse<FollowUserResponse>> getFollowings(
      @PathVariable Long id,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(
        PageResponse.from(
            followService.getFollowings(id, pageable), FollowUserResponse::fromFollowing));
  }

  @GetMapping("/api/v1/user/{id}/follow/count")
  public ResponseEntity<FollowCountResponse> getFollowCount(@PathVariable Long id) {
    FollowService.FollowCountResult count = followService.getFollowCount(id);
    return ResponseEntity.ok(
        new FollowCountResponse(count.followersCount(), count.followeesCount()));
  }

  @GetMapping("/api/v1/user/{id}/follow/status")
  public ResponseEntity<FollowStatusResponse> getFollowStatus(
      @PathVariable Long id, @CurrentUser AuthUser authUser) {
    requireAuth(authUser);
    boolean isFollowing = followService.isFollowing(authUser.getId(), id);
    return ResponseEntity.ok(new FollowStatusResponse(isFollowing));
  }

  private static void requireAuth(AuthUser authUser) {
    if (authUser == null) {
      throw new AccessDeniedException("인증이 필요합니다.");
    }
  }
}
