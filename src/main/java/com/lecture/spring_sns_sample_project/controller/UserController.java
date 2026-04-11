package com.lecture.spring_sns_sample_project.controller;

import com.lecture.spring_sns_sample_project.controller.dto.PageResponse;
import com.lecture.spring_sns_sample_project.controller.dto.UserCreateRequest;
import com.lecture.spring_sns_sample_project.controller.dto.UserResponse;
import com.lecture.spring_sns_sample_project.controller.dto.UserUpdateRequest;
import com.lecture.spring_sns_sample_project.domain.user.User;
import com.lecture.spring_sns_sample_project.domain.user.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @PostMapping("/api/user")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody UserCreateRequest request) {
    User user = userService.register(request.toEntity());
    UserResponse response = UserResponse.from(user);
    return ResponseEntity.created(URI.create("/api/user/" + user.getId())).body(response);
  }

  @GetMapping("/api/user/{id}")
  public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
    User user = userService.getById(id);
    return ResponseEntity.ok(UserResponse.from(user));
  }

  @GetMapping("/api/user")
  public ResponseEntity<PageResponse<UserResponse>> getUsers(
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    PageResponse<UserResponse> response =
        PageResponse.from(userService.getAll(pageable), UserResponse::from);
    return ResponseEntity.ok(response);
  }

  @PutMapping("/api/user/{id}")
  public ResponseEntity<UserResponse> updateUser(
      @PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
    User user = userService.update(id, request.nickname(), request.password());
    return ResponseEntity.ok(UserResponse.from(user));
  }

  @DeleteMapping("/api/user/{id}")
  public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    userService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
