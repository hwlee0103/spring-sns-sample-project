package com.lecture.spring_sns_sample_project.domain.media;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaRepository extends JpaRepository<Media, Long> {

  /** 정리 배치용 — INIT/UPLOADED 상태에서 일정 시간 초과된 미디어. */
  @Query(
      "SELECT m FROM Media m WHERE m.status IN :statuses AND m.createdAt < :threshold"
          + " AND m.deletedAt IS NULL")
  List<Media> findByStatusInAndCreatedAtBefore(
      @Param("statuses") List<MediaStatus> statuses, @Param("threshold") Instant threshold);

  /** 사용자의 미디어 목록. */
  List<Media> findByUserIdAndDeletedAtIsNull(Long userId);
}
