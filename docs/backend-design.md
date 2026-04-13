# Backend Design — Spring Boot SNS API

> 이 문서는 백엔드 아키텍처, 핵심 컨벤션, 도메인 모델, API 계약을 정의한다.
> 자세한 코드 규칙은 `.claude/skills/spring-api-rules/SKILL.md` 를 참조한다.

## 1. 개요

스레드/인스타그램/트위터 스타일 SNS 의 REST API. Spring Boot 4.0.5 + Java 26 기반.

| 항목 | 선택 | 비고 |
|------|------|------|
| Framework | Spring Boot 4.0.5 | Web MVC + Data JPA |
| Language | Java 26 | record, sealed class 활용 |
| Build | Gradle (Kotlin DSL) | Spotless 자동 포맷 |
| Persistence | JPA / Hibernate | H2 (dev), PostgreSQL (prod) |
| Auth | Spring Security 6 | HttpSession + Spring Session Redis |
| Session | Spring Session + Redis | `RedisIndexedSessionRepository`, Sliding 30분 |
| Password | Argon2 | `spring-security-crypto` |
| Validation | Bean Validation (Jakarta) | `@Valid` + Hibernate Validator |
| 테스트 | JUnit 5 | shell 기반 통합 테스트 (`src/main/resources/http/`) |

## 2. 패키지 구조

```
com.lecture.spring_sns_sample_project
├── controller/                  # @RestController
│   ├── AuthController.java
│   ├── UserController.java
│   ├── PostController.java
│   └── dto/                     # Request/Response Java records
│       ├── LoginRequest.java
│       ├── UserCreateRequest.java
│       ├── UserUpdateRequest.java
│       ├── UserResponse.java
│       ├── PostCreateRequest.java
│       ├── PostUpdateRequest.java
│       ├── PostResponse.java
│       └── PageResponse.java    # 공통 페이지네이션 응답 record
├── domain/
│   ├── common/
│   │   └── DomainException.java # 모든 도메인 예외의 abstract 부모
│   ├── user/
│   │   ├── User.java            # @Entity
│   │   ├── UserRepository.java
│   │   ├── UserService.java
│   │   ├── UserException.java   # extends DomainException
│   │   └── UserDetailsServiceImpl.java
│   └── post/
│       ├── Post.java
│       ├── PostRepository.java
│       ├── PostService.java
│       └── PostException.java
└── config/
    ├── SecurityConfig.java
    ├── PasswordEncoderConfig.java
    └── GlobalExceptionHandler.java
```

## 3. 레이어 책임

```
┌─────────────────┐
│   Controller    │  HTTP 입출력, DTO ↔ Entity 변환, 검증 트리거
├─────────────────┤
│     Service     │  비즈니스 로직, 트랜잭션 경계, 권한 체크
├─────────────────┤
│   Repository    │  Spring Data JPA, 쿼리 메서드
├─────────────────┤
│  Entity/Domain  │  불변식, 상태 변경 메서드, 도메인 예외 발생
└─────────────────┘
```

### 핵심 원칙
- **DI**: 생성자 주입(`@RequiredArgsConstructor`). 필드 `@Autowired` 금지.
- **DTO**: Java record. Request 는 `toEntity()`, Response 는 `from(Entity)` 정적 팩토리.
- **Entity**: `protected` 기본 생성자, `GenerationType.IDENTITY`, `FetchType.LAZY`, FK 제약 없음.
- **`@Transactional`**: dirty checking 또는 multi-write 에만 사용.
- **검증**: Controller(`@Valid`) + Service(외부 의존성 호출 전 raw 값 검증) + Entity(불변식) 3중 방어.

## 4. 도메인 모델

### 4.1 User

| 필드 | 타입 | 제약 |
|------|------|------|
| id | Long | PK, IDENTITY |
| email | String | unique, not null |
| password | String | not null, Argon2 인코딩 |
| nickname | String | not null |

**불변식 (User 생성/수정 시점)**
- `email`: not blank, 형식 검증 (Bean Validation)
- `password`: 8~64자 (raw), 저장 시 Argon2 인코딩
- `nickname`: 2~20자

**상태 변경 메서드**
- `update(nickname, password)` — null/blank 검증 후 갱신
- `encodePassword(passwordEncoder)` — register 경로에서 사용 (현재 패턴 비일관, Review #10 진행중)

### 4.2 Post

| 필드 | 타입 | 제약 |
|------|------|------|
| id | Long | PK, IDENTITY |
| author | User | `@ManyToOne(LAZY)`, FK 제약 없음 |
| content | String | not null, 1~500자 |
| createdAt | Instant | not null, 생성 시 자동 설정 |

**불변식**
- `author`: not null
- `content`: not blank, 500자 이하

**도메인 메서드**
- `updateContent(content)` — 길이/blank 검증
- `isAuthor(userId)` — 권한 체크 헬퍼

## 5. API 계약

베이스 경로: `/api`

### 5.1 인증

| Method | Path | 설명 | 인증 | 상태 |
|--------|------|------|-----|------|
| POST | `/api/auth/login` | 로그인 (세션 생성) | 공개 | 200 / 401 |
| POST | `/api/auth/logout` | 로그아웃 (세션 무효화) | 인증 | 204 |
| GET | `/api/auth/me` | 현재 사용자 조회 | 공개 | 200 / 401 |

**POST /api/auth/login**
```json
// Request
{ "email": "a@b.com", "password": "..." }
// Response 200
{ "id": 1, "email": "a@b.com", "nickname": "alice" }
// Response 401 — 인증 실패
```

### 5.2 사용자

| Method | Path | 설명 | 인증 |
|--------|------|------|-----|
| POST | `/api/user` | 회원가입 | 공개 |
| GET | `/api/user` | 사용자 목록 (페이징) | 공개 |
| GET | `/api/user/{id}` | 단일 사용자 조회 | 공개 |
| PUT | `/api/user/{id}` | 사용자 정보 수정 | 인증 |
| DELETE | `/api/user/{id}` | 사용자 삭제 | 인증 |

### 5.3 게시글 (Post)

| Method | Path | 설명 | 인증 |
|--------|------|------|-----|
| POST | `/api/post` | 게시글 작성 | 인증 |
| GET | `/api/post` | 피드 조회 (페이징, 최신순) | 공개 |
| GET | `/api/post/{id}` | 단일 게시글 조회 | 공개 |
| PUT | `/api/post/{id}` | 게시글 수정 | 인증 (작성자만) |
| DELETE | `/api/post/{id}` | 게시글 삭제 | 인증 (작성자만) |

**페이징 파라미터** (Spring Data 기본)
- `?page=0&size=20&sort=id,desc`

**페이지 응답 형태** (`PageResponse<T>`)
```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "first": true,
  "last": false
}
```

## 6. 인증 / 세션

- **메커니즘**: HttpSession + **Spring Session Redis** (`RedisIndexedSessionRepository`). 중앙 집중식 세션 저장.
- **쿠키**: `SESSION` (Spring Session 기본). HttpOnly, Secure(prod), SameSite=Lax.
- **CSRF**: `CookieCsrfTokenRepository` + `XorCsrfTokenRequestAttributeHandler` (BREACH 완화).
- **동시 세션**: `SpringSessionBackedSessionRegistry` — 사용자당 최대 3개, 초과 시 가장 오래된 세션 만료.
- **세션 만료**: Sliding Session 30분 (idle) + Absolute 24시간.
- **저장**: `HttpSessionSecurityContextRepository` 가 SecurityContext 를 세션에 저장. Redis 가 백엔드 저장소.
- **인가 매트릭스**:
  - `POST /api/user`, `/api/auth/login`, `/api/auth/me`: 공개
  - `GET /api/user`, `GET /api/user/*`, `GET /api/post`, `GET /api/post/*`: 공개 (브라우징)
  - 그 외 모두 인증 필요

## 7. 예외 처리

```
DomainException (abstract)
├── UserException
│   ├── emailAlreadyExists(email)
│   ├── notFound(id) / notFoundByEmail(email)
│   └── invalidField(fieldName)
└── PostException
    ├── notFound(id)
    ├── invalidField / contentTooLong
    └── forbidden(id)
```

`GlobalExceptionHandler` 는 다음 두 핸들러로 단일화:

| 예외 | HTTP | 응답 |
|------|------|------|
| `DomainException` (모든 하위 포함) | 400 | `{ "message": "..." }` |
| `MethodArgumentNotValidException` | 400 | `{ "message": "...", "errors": { "field": "..." } }` |

> **TODO** (Review #14): `notFound` 류는 의미상 404 가 적절. `DomainException` 에 HTTP 상태를 노출하거나 `NotFoundException` 하위 타입 도입 검토.

## 8. 검증 전략

3계층 방어:

1. **Controller** — `@Valid` + Bean Validation (`@NotBlank`, `@Email`, `@Size`)
2. **Service** — 외부 의존성(예: `passwordEncoder.encode`) 호출 전 raw 값 검증 (NPE 방지)
3. **Entity** — 상태 변경 메서드 진입 시점 불변식 검증, 위반 시 도메인 예외

내부 호출 경로(Bean Validation 우회) 에서도 도메인 일관성이 보장되도록 중복 검증을 허용.

## 9. 페이지네이션

- 컬렉션 조회는 무조건 `Pageable` + `Page<Entity>` 사용.
- Controller 는 `@PageableDefault(size = N, sort = "id", direction = DESC)` 로 기본값 명시.
- Response 는 `PageResponse<T>` 로 감싸 메타데이터(`first`, `last`, `totalPages` 등) 함께 전달.
- 프론트 무한 스크롤이 `last` 플래그를 활용한다.

## 10. 빌드 / 실행

```bash
./gradlew build              # 빌드 (Spotless + 컴파일 + 테스트)
./gradlew test               # 테스트만
./gradlew bootRun            # 애플리케이션 실행
./gradlew spotlessApply      # 포맷 자동 수정
src/main/resources/http/user.sh  # API 통합 테스트 (curl)
```

## 11. 알려진 제약 / TODO

- [ ] CSRF 비활성화 — 운영 전 토큰 도입
- [ ] Review #9 — Entity 가 `PasswordEncoder` 에 직접 의존 (도메인 순수성 위배)
- [ ] Review #10 — `register` vs `update` 인코딩 패턴 비일관 (부분 해결)
- [ ] Review #13 — `UserService.delete` 두 번 DB 접근 + 트랜잭션 미적용
- [ ] Review #14 — NotFound 응답을 404 로 분기
- [ ] Post 이미지 첨부 (presigned URL 패턴)
- [ ] Like / Comment / Follow 도메인 추가
- [ ] PostgreSQL 마이그레이션 (Flyway)
- [ ] 통합 테스트 (`@SpringBootTest`)
