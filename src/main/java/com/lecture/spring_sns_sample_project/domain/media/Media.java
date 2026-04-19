package com.lecture.spring_sns_sample_project.domain.media;

import com.lecture.spring_sns_sample_project.domain.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 미디어(이미지/동영상) 도메인 Entity.
 *
 * <p>업로드 상태 머신(INIT → UPLOADED → COMPLETED / FAILED)을 관리하며, 오브젝트 스토리지(S3) 내 파일 경로와 확장 메타데이터를 저장한다.
 *
 * <p>{@code attributes} 는 JSON 문자열로, 이미지의 해상도/크기, 동영상의 재생시간, 멀티파트 업로드의 파트 정보(번호/ETag) 등을 유연하게 저장한다.
 */
@Entity
@Table(
    name = "media",
    indexes = {
      @Index(name = "idx_media_user_id", columnList = "user_id"),
      @Index(name = "idx_media_status", columnList = "status")
    })
@Getter
public class Media extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private MediaType mediaType;

  /** 오브젝트 스토리지 내 파일 경로. 예: {@code media/1/42/photo.jpg}. */
  @Column(nullable = false, length = 500)
  private String path;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private MediaStatus status;

  /** 업로드한 사용자 ID. FK 제약 없음. */
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /** S3 멀티파트 업로드 ID. 단일 파일 업로드 시 null. */
  @Column(length = 255)
  private String uploadId;

  /**
   * 확장 메타데이터 (JSON 문자열).
   *
   * <p>이미지: width/height/format/sizeBytes. 동영상: width/height/durationSeconds/format/sizeBytes.
   * 멀티파트: parts[{partNumber, eTag, sizeBytes}].
   */
  @Column(columnDefinition = "TEXT")
  private String attributes;

  protected Media() {}

  public Media(Long userId, MediaType mediaType, String path) {
    if (userId == null) {
      throw MediaException.invalidField("userId");
    }
    if (mediaType == null) {
      throw MediaException.invalidField("mediaType");
    }
    if (path == null || path.isBlank()) {
      throw MediaException.invalidField("path");
    }
    this.userId = userId;
    this.mediaType = mediaType;
    this.path = path;
    this.status = MediaStatus.INIT;
  }

  /** 멀티파트 업로드 시 uploadId 설정. */
  public void assignUploadId(String uploadId) {
    if (uploadId == null || uploadId.isBlank()) {
      throw MediaException.invalidField("uploadId");
    }
    this.uploadId = uploadId;
  }

  /** 업로드 완료 신호 — INIT → UPLOADED. */
  public void markUploaded() {
    if (this.status != MediaStatus.INIT) {
      throw MediaException.invalidStatusTransition(this.status, MediaStatus.UPLOADED);
    }
    this.status = MediaStatus.UPLOADED;
  }

  /** 검증 완료 — UPLOADED → COMPLETED. 메타데이터 저장. */
  public void markCompleted(String attributes) {
    if (this.status != MediaStatus.UPLOADED) {
      throw MediaException.invalidStatusTransition(this.status, MediaStatus.COMPLETED);
    }
    this.status = MediaStatus.COMPLETED;
    this.attributes = attributes;
  }

  /** 실패 처리 — 어떤 상태에서든 FAILED 로 전이 가능. */
  public void markFailed() {
    this.status = MediaStatus.FAILED;
  }

  /** 메타데이터 업데이트 (멀티파트 파트 정보 임시 저장 등). */
  public void updateAttributes(String attributes) {
    this.attributes = attributes;
  }

  public boolean isCompleted() {
    return this.status == MediaStatus.COMPLETED;
  }

  public boolean isOwner(Long userId) {
    return this.userId != null && this.userId.equals(userId);
  }
}
