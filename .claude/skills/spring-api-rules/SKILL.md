---
name: spring-api-rules
description: Define controllers, services, repositories, entities, DTOs for Spring Boot REST API. Use when user mentions API, endpoint, controller, service, repository, entity, DTO, CRUD, domain, feature, function, or REST creation.
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, LSP
---

# Spring API Development Rules

Standard rules for Spring Boot REST API development in this project.

## Package Structure

```
com.apiece.springboot_sns_sample
├── controller/              # REST API controllers
│   └── dto/                 # Request/Response DTOs
├── domain/                  # Domain packages
│   └── user/                # User domain
│       ├── User.java        # Entity
│       ├── UserRepository.java
│       ├── UserService.java
│       └── UserException.java
└── config/                  # Configuration classes
```

## Common Rules

- Constructor injection using `@RequiredArgsConstructor` where possible
- No field injection (`@Autowired` on fields)
- `@ConfigurationProperties` classes should be written as records
- Use Lombok `@Getter` actively for entities and classes (except DTOs which use records)

## Null / Blank Validation (필수)

도메인/서비스 계층의 입력값은 항상 null·빈 문자열·공백 문자열 여부를 검증한다. 검증을 빼먹지 말 것.

- **Entity 상태 변경 메서드**: 메서드 진입 즉시 파라미터 검증. 위반 시 도메인 예외(`{Domain}Exception.invalidField(...)` 등) 발생.
  - String: `value == null || value.isBlank()`
  - Collection: `value == null || value.isEmpty()`
  - Object: `Objects.requireNonNull(value, "...")`
- **Service 메서드**: Entity 호출 이전에 외부 의존성(예: `PasswordEncoder.encode(...)`)이 먼저 실행되는 경우, 그 의존성 호출 전에 raw 값 검증을 선행한다 (NPE 방지).
- **Controller DTO (record)**: Bean Validation(`@NotBlank`, `@NotNull`, `@Size` 등)을 적용하고 컨트롤러 메서드 파라미터에 `@Valid` 를 붙인다.
- 검증 실패 메시지는 어느 필드인지 식별 가능해야 한다 (단순 "invalid input" 금지).
- 동일 검증을 여러 계층에서 중복하더라도 방어적 구현이 우선이다 (Entity 불변식 + Service 입력 검증).

## Controller

- Use `@RestController`
- Do not use `@RequestMapping` at class level; write full endpoint path on each method
- Return type: `ResponseEntity<T>`
- Naming: `*Controller`

## DTO

- Only controller DTOs go in `controller/dto` package
- Use Java `record`
- Request: `toEntity()` method
- Response: `from(Entity)` static factory
- 목록 응답은 `List<T>` 가 아닌 `PageResponse<T>` 로 감싸 페이지 메타데이터를 함께 반환한다.

## Pagination (필수)

- 컬렉션 전체 조회 엔드포인트는 항상 `Pageable` 을 받아 페이징 처리한다 — `findAll()` 직접 노출 금지.
- Controller: `@PageableDefault(size = N, sort = "...", direction = ...)` 로 기본값 명시.
- Service: `Page<Entity>` 반환. Repository는 Spring Data JPA의 `findAll(Pageable)` 또는 커스텀 쿼리 메서드를 사용.
- Response: `PageResponse.from(page, EntityResponse::from)` 으로 변환하여 `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last` 를 포함한다.

## Domain

Each domain is organized under `domain/{domainName}/` package:

- `{Domain}.java` - Entity
- `{Domain}Repository.java` - Data access
- `{Domain}Service.java` - Business logic
- `{Domain}Exception.java` - Domain exception

### Entity

- `protected` default constructor
- `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- Associations: `FetchType.LAZY` by default
- No FK constraints in database; use `@JoinColumn` without FK constraints

### Repository

- Extends `JpaRepository<Entity, ID>`
- Follow Spring Data JPA query method naming conventions

### Service

- Use `@Transactional` only when necessary:
    - Use when multiple write operations must be in a single transaction
    - Use when Dirty Checking is needed (entity modification without explicit save)
    - Do NOT use for single Repository operations (they handle transactions automatically)
    - Do NOT use for simple read operations

## Exception Handling

- 모든 도메인 예외는 `domain/common/DomainException` 을 상속한다 (`abstract` 부모 클래스).
- 도메인별 예외: `domain/{domainName}/{Domain}Exception.java` — 정적 팩토리 메서드로 의미 있는 메시지 제공.
- 전역 처리: `@RestControllerAdvice` 에서 `DomainException` 을 단일 핸들러로 처리하여 도메인 추가 시 핸들러 변경이 필요 없도록 한다.
- Bean Validation 실패는 `MethodArgumentNotValidException` 핸들러에서 필드별 에러로 응답한다.

## API Shell Script
## 테스트용 프로그램

When creating a new API, create a shell script in `src/main/resources/http/`:

- File naming: lowercase with resource name (e.g., `post.sh`, `follow.sh`)
- Include curl commands for all endpoints (POST, GET, PUT, DELETE)
- Use `BASE_URL="http://localhost:8080"` variable
- Use `-b cookies.txt` for authenticated requests
- Add descriptive echo statements before each curl command
- Start with `#!/bin/bash` shebang
