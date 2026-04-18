package com.lecture.spring_sns_sample_project.domain.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;

/**
 * 공통 감사(audit) 필드를 제공하는 매핑 슈퍼클래스.
 *
 * <p>{@code @PrePersist} / {@code @PreUpdate} 콜백으로 {@code createdAt}, {@code updatedAt} 을 자동 관리한다.
 * {@code deletedAt} 은 soft delete 시 도메인 메서드에서 수동 설정한다.
 *
 * <p>필드 접근 제어자를 {@code protected} 로 두어, 하위 엔티티(예: Follow.restore)에서 {@code createdAt} 을 직접 재설정할 수
 * 있도록 한다.
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

  /**
   * 최초 생성 시각. {@code @PrePersist} 에서 자동 설정.
   *
   * <p>{@code updatable = false} 를 사용하지 않는 이유: Follow.restore() 등 일부 엔티티에서 재설정이 필요하며, Hibernate 가
   * UPDATE SQL 에서 이 컬럼을 제외하면 DB 에 반영되지 않기 때문.
   */
  @Column(nullable = false)
  protected Instant createdAt;

  @Column(nullable = false)
  protected Instant updatedAt;

  @Column protected Instant deletedAt;

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
