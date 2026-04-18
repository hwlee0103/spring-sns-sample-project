package com.lecture.spring_sns_sample_project.controller.dto;

import com.lecture.spring_sns_sample_project.domain.post.Post;
import jakarta.validation.constraints.Size;

/**
 * 게시글 생성 요청 — 통합 (일반/답글/인용/리포스트).
 *
 * <p>content: 리포스트 시 null 허용, 그 외 필수. Bean Validation 으로 길이만 체크하고 not-blank 검증은 Service 에서 유형별로 분기
 * 처리.
 */
public record PostCreateRequest(
    @Size(max = Post.MAX_CONTENT_LENGTH, message = "content는 1000자를 초과할 수 없습니다.") String content,
    Long parentId,
    Long quoteId,
    Long repostId) {}
