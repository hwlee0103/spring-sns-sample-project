package com.lecture.spring_sns_sample_project.domain.media;

/**
 * 미디어 업로드 상태 머신.
 *
 * <pre>
 * INIT → UPLOADED → COMPLETED
 *   │        │
 *   └→ FAILED ←┘
 * </pre>
 */
public enum MediaStatus {
  /** Presigned URL 발급 완료. 클라이언트 업로드 대기 중. */
  INIT,
  /** 클라이언트가 업로드 완료 신호 전송. 서버 검증 대기 중. */
  UPLOADED,
  /** 검증 완료. 게시글에 연결 가능. */
  COMPLETED,
  /** 업로드 실패 또는 타임아웃. 정리 대상. */
  FAILED
}
