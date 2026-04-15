package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.post.Post;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
    @NotBlank(message = "content는 필수값입니다.")
        @Size(max = Post.MAX_CONTENT_LENGTH, message = "content는 500자를 초과할 수 없습니다.")
        String content) {}
