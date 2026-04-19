# Media Service Design — 미디어 도메인 설계

> 이 문서는 미디어(이미지/동영상) 도메인의 엔티티, 업로드 흐름, 오브젝트 스토리지 연동 전략을 정의한다.

## 1. 개요

### 1.1 왜 별도 도메인인가

| 방식 | 장점 | 단점 |
|------|------|------|
| Post 에 URL 컬럼 추가 | 단순 | 미디어 상태 관리 불가, 멀티파트 업로드 추적 불가 |
| Post.metadata JSONB | 스키마 유연 | 상태 전이(INIT→UPLOADED→COMPLETED) 추적 어려움 |
| **별도 Media 도메인** | 상태 머신, 멀티파트 추적, 재사용, 독립 라이프사이클 | 테이블 추가 |

> **선택**: 별도 도메인 — 미디어는 업로드 시작 → 전송 → 완료 → 게시글 연결의 독립적인 라이프사이클을 가진다. Post 와 1:1 이 아닌 독립 관리가 필요.

### 1.2 업로드 흐름 개요

```
[클라이언트]
  1. POST /api/v1/media/presigned-url
     { "mediaType": "IMAGE", "fileName": "photo.jpg" }
     → 서버: Media(INIT) 생성 + S3 Presigned URL 발급
     ← { "mediaId": 1, "uploadUrl": "https://s3.../presigned...", "path": "media/1/photo.jpg" }

  2. PUT uploadUrl (클라이언트 → S3 직접 업로드, 서버 미경유)

  3. POST /api/v1/media/{id}/complete
     → 서버: S3 파일 존재 확인 + Media(COMPLETED) 상태 전이
     ← { "mediaId": 1, "status": "COMPLETED", "path": "media/1/photo.jpg" }

  4. POST /api/v1/post
     { "content": "사진 올립니다", "mediaId": 1 }
     → 서버: Post 생성 + Post.mediaId = 1 연결
```

## 2. Entity 설계

### 2.1 필드 정의

| 필드 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | Long | PK, IDENTITY | — |
| mediaType | MediaType | not null | IMAGE / VIDEO |
| path | String | not null | 오브젝트 스토리지 내 파일 경로 (예: `media/1/photo.jpg`) |
| status | MediaStatus | not null, default INIT | 업로드 상태 머신 |
| userId | Long | not null | 업로드한 사용자 ID (FK 제약 없음) |
| uploadId | String | nullable | S3 멀티파트 업로드 ID. 대용량 파일(동영상) 분할 업로드 시 사용 |
| attributes | String (JSON) | nullable | 확장 메타데이터 — 해상도, 재생시간, 멀티파트 파트 번호/ETag 등 |
| createdAt | Instant | not null (BaseEntity) | 생성 시각 |
| updatedAt | Instant | not null (BaseEntity) | 수정 시각 |
| deletedAt | Instant | nullable (BaseEntity) | 삭제 시각 |

### 2.2 MediaType enum

```java
public enum MediaType {
    IMAGE,   // JPEG, PNG, WebP, GIF
    VIDEO    // MP4, WebM, MOV
}
```

### 2.3 MediaStatus enum — 상태 머신

```
INIT ──────→ UPLOADED ──────→ COMPLETED
  │              │
  └──→ FAILED ←──┘
```

| 상태 | 설명 | 전이 조건 |
|------|------|-----------|
| **INIT** | Presigned URL 발급 완료. 클라이언트 업로드 대기 중 | 초기 상태 |
| **UPLOADED** | 클라이언트가 S3 업로드 완료 신호 전송 (멀티파트의 경우 모든 파트 업로드 완료) | 클라이언트 `/complete` 호출 |
| **COMPLETED** | S3 파일 존재 확인 + 메타데이터 추출 완료. 게시글에 연결 가능 | 서버 검증 통과 |
| **FAILED** | 업로드 실패 또는 타임아웃. 정리 대상 | 검증 실패 또는 TTL 만료 |

```java
public enum MediaStatus {
    INIT,       // Presigned URL 발급 완료
    UPLOADED,   // 업로드 완료 신호 수신
    COMPLETED,  // 검증 완료 — 게시글 연결 가능
    FAILED      // 실패 — 정리 대상
}
```

### 2.4 path — 오브젝트 스토리지 경로

```
media/{userId}/{mediaId}/{originalFileName}

예시:
  media/1/42/photo.jpg          — 이미지
  media/1/43/video.mp4          — 동영상
  media/1/43/thumbnail.jpg      — 동영상 썸네일 (향후)
```

| 세그먼트 | 목적 |
|---------|------|
| `media/` | 버킷 내 미디어 전용 prefix |
| `{userId}/` | 사용자별 격리 — 접근 제어 + 정리 용이 |
| `{mediaId}/` | 미디어별 디렉토리 — 썸네일 등 파생 파일 공존 |
| `{fileName}` | 원본 파일명 (URL-safe 인코딩) |

### 2.5 uploadId — 멀티파트 업로드

대용량 파일(주로 동영상)은 단일 PUT 으로 업로드할 수 없다. S3 멀티파트 업로드를 사용한다.

```
[멀티파트 업로드 흐름]

1. 서버: CreateMultipartUpload → uploadId 발급
2. 서버: 파트별 Presigned URL 발급 (5MB 단위)
3. 클라이언트: 각 파트를 개별 PUT 으로 업로드
4. 클라이언트: 각 파트의 ETag 수집
5. 클라이언트: POST /api/v1/media/{id}/complete-multipart
   { "parts": [{ "partNumber": 1, "eTag": "abc..." }, ...] }
6. 서버: CompleteMultipartUpload 호출 → 파트 합치기
7. 서버: Media 상태 → COMPLETED
```

> **uploadId**: S3 가 멀티파트 업로드 세션을 식별하는 고유 ID. 이 값이 있어야 파트 업로드 URL 을 발급하고, 최종 합치기를 호출할 수 있다.

### 2.6 attributes — 확장 메타데이터 (JSON)

```json
// IMAGE 예시
{
  "width": 1080,
  "height": 1350,
  "format": "JPEG",
  "sizeBytes": 245000,
  "altText": "사진 설명"
}

// VIDEO 예시
{
  "width": 1920,
  "height": 1080,
  "durationSeconds": 30,
  "format": "MP4",
  "sizeBytes": 15000000,
  "thumbnailPath": "media/1/43/thumbnail.jpg"
}

// 멀티파트 업로드 진행 중 (임시 저장)
{
  "parts": [
    { "partNumber": 1, "eTag": "abc123", "sizeBytes": 5242880 },
    { "partNumber": 2, "eTag": "def456", "sizeBytes": 5242880 },
    { "partNumber": 3, "eTag": "ghi789", "sizeBytes": 3145728 }
  ],
  "totalParts": 3,
  "totalSizeBytes": 13631488
}
```

> **왜 JSON String 으로 저장하는가**: 미디어 유형(IMAGE/VIDEO)에 따라 속성이 다르고, 멀티파트 진행 중에는 파트 정보를 임시 저장해야 한다. 정규화된 컬럼으로는 유연성이 부족. PostgreSQL JSONB 가 이상적이지만 H2 호환을 위해 `TEXT` 로 저장하고 애플리케이션에서 Jackson 으로 파싱.

### 2.7 Entity 코드

```java
@Entity
@Table(name = "media",
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

    /** 오브젝트 스토리지 내 파일 경로. */
    @Column(nullable = false)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaStatus status;

    /** 업로드한 사용자 ID. FK 제약 없음. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** S3 멀티파트 업로드 ID. 단일 파일 업로드 시 null. */
    @Column
    private String uploadId;

    /**
     * 확장 메타데이터 (JSON).
     * 이미지: width/height/format/sizeBytes
     * 동영상: width/height/durationSeconds/format/sizeBytes/thumbnailPath
     * 멀티파트: parts[{partNumber, eTag, sizeBytes}]
     */
    @Column(columnDefinition = "TEXT")
    private String attributes;

    protected Media() {}

    public Media(Long userId, MediaType mediaType, String path) {
        if (userId == null) throw MediaException.invalidField("userId");
        if (mediaType == null) throw MediaException.invalidField("mediaType");
        if (path == null || path.isBlank()) throw MediaException.invalidField("path");
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

    /** 검증 완료 — UPLOADED → COMPLETED. */
    public void markCompleted(String attributes) {
        if (this.status != MediaStatus.UPLOADED) {
            throw MediaException.invalidStatusTransition(this.status, MediaStatus.COMPLETED);
        }
        this.status = MediaStatus.COMPLETED;
        this.attributes = attributes;
    }

    /** 실패 처리. */
    public void markFailed() {
        this.status = MediaStatus.FAILED;
    }

    /** 메타데이터 업데이트. */
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
```

## 3. 상태 전이 규칙

| 현재 상태 | 허용 전이 | 메서드 | 트리거 |
|----------|----------|--------|--------|
| INIT | → UPLOADED | `markUploaded()` | 클라이언트 완료 신호 |
| INIT | → FAILED | `markFailed()` | 타임아웃/오류 |
| UPLOADED | → COMPLETED | `markCompleted(attributes)` | 서버 검증 통과 |
| UPLOADED | → FAILED | `markFailed()` | S3 파일 미존재/손상 |
| COMPLETED | (최종 상태) | — | — |
| FAILED | (최종 상태) | — | — |

> **잘못된 전이 시**: `MediaException.invalidStatusTransition()` 발생 → 400.

## 4. Post 연결

```java
// Post.java
@Column
private Long mediaId;  // Media 도메인의 ID. FK 제약 없음.
```

| 규칙 | 설명 |
|------|------|
| Post 1 : Media 0..1 | 게시글당 미디어 0~1개 (향후 N개 확장 시 별도 조인 테이블) |
| COMPLETED 상태만 연결 가능 | `PostService.create()` 에서 `media.isCompleted()` 검증 |
| Media 삭제 시 Post.mediaId 유지 | "[미디어 삭제됨]" 표시 (orphan 허용) |

## 5. API 계약

### 5.1 엔드포인트

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/v1/media/presigned-url` | Presigned URL 발급 (단일 파일) | 인증 |
| POST | `/api/v1/media/multipart/init` | 멀티파트 업로드 초기화 | 인증 |
| POST | `/api/v1/media/multipart/{id}/presign-part` | 멀티파트 파트 Presigned URL | 인증 |
| POST | `/api/v1/media/{id}/complete` | 업로드 완료 (단일/멀티파트) | 인증 |
| GET | `/api/v1/media/{id}` | 미디어 정보 조회 | 공개 |
| DELETE | `/api/v1/media/{id}` | 미디어 삭제 | 인증 (본인) |

### 5.2 요청/응답

**POST /api/v1/media/presigned-url — 단일 파일 Presigned URL 발급**

```json
// Request
{ "mediaType": "IMAGE", "fileName": "photo.jpg" }

// Response 201
{
  "mediaId": 42,
  "uploadUrl": "https://s3.amazonaws.com/bucket/media/1/42/photo.jpg?X-Amz-...",
  "path": "media/1/42/photo.jpg"
}
```

**POST /api/v1/media/{id}/complete — 업로드 완료**

```json
// Request (멀티파트의 경우 parts 포함)
{
  "parts": [
    { "partNumber": 1, "eTag": "\"abc123...\"" },
    { "partNumber": 2, "eTag": "\"def456...\"" }
  ]
}

// Request (단일 파일의 경우 body 없이 호출)

// Response 200
{
  "mediaId": 42,
  "status": "COMPLETED",
  "mediaType": "IMAGE",
  "path": "media/1/42/photo.jpg",
  "attributes": { "width": 1080, "height": 1350, "format": "JPEG", "sizeBytes": 245000 }
}
```

## 6. 인덱스 전략

```sql
CREATE INDEX idx_media_user_id ON media (user_id);
CREATE INDEX idx_media_status ON media (status);

-- 정리 배치용: INIT 상태 + 생성 시간 오래된 항목
CREATE INDEX idx_media_status_created
    ON media (status, created_at)
    WHERE status IN ('INIT', 'UPLOADED');
```

## 7. 정리 배치 (Cleanup Scheduler)

INIT/UPLOADED 상태에서 일정 시간(예: 1시간) 이 지나면 업로드 실패로 간주.

```java
@Scheduled(fixedRate = 3600_000)  // 1시간
public void cleanupStaleMedia() {
    Instant threshold = Instant.now().minus(Duration.ofHours(1));
    List<Media> stale = mediaRepository.findByStatusInAndCreatedAtBefore(
        List.of(MediaStatus.INIT, MediaStatus.UPLOADED), threshold);
    for (Media media : stale) {
        media.markFailed();
        // S3 파일 삭제 (있으면)
        storageService.deleteIfExists(media.getPath());
        if (media.getUploadId() != null) {
            storageService.abortMultipartUpload(media.getPath(), media.getUploadId());
        }
    }
}
```

## 8. Flyway 마이그레이션 (V6)

```sql
CREATE TABLE media (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    media_type  VARCHAR(10)  NOT NULL,
    path        VARCHAR(500) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'INIT',
    user_id     BIGINT       NOT NULL,
    upload_id   VARCHAR(255),
    attributes  TEXT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_media_user_id ON media (user_id);
CREATE INDEX idx_media_status ON media (status);
CREATE INDEX idx_media_status_created
    ON media (status, created_at)
    WHERE status IN ('INIT', 'UPLOADED');
```

## 9. 보안 고려

| 항목 | 대응 |
|------|------|
| Presigned URL 유효 시간 | 15분 (짧게 설정) |
| 파일 크기 제한 | IMAGE: 10MB, VIDEO: 100MB (Presigned URL 생성 시 조건 포함) |
| Content-Type 검증 | Presigned URL 에 Content-Type 조건 부여 |
| 본인 확인 | 완료/삭제 시 `media.isOwner(userId)` 검증 |
| S3 버킷 접근 | Presigned URL 만 외부 노출. 버킷 자체는 private |
| 파일 이름 새니타이징 | 경로 순회 공격 방지 (`../` 제거, URL-safe 인코딩) |

## 10. 향후 확장

- [ ] 다중 미디어 (Post : Media = 1 : N → 중간 테이블 `post_media`)
- [ ] 썸네일 자동 생성 (Lambda/서버리스)
- [ ] 동영상 트랜스코딩 (FFmpeg/MediaConvert)
- [ ] CDN URL 매핑 (CloudFront/Cloudflare)
- [ ] 이미지 리사이즈 (on-the-fly 또는 사전 생성)
- [ ] 악성 파일 스캔 (ClamAV)
