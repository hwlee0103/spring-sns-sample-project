# Test Design — Backend Testing Strategy

> 이 문서는 Spring Boot SNS 백엔드의 테스트 방법론, 레이어별 전략, 도구 선택 근거를 정의한다.

## 1. 테스트 방법론 비교

### 1.1 단위 테스트 (Unit Test)

| 항목 | 내용 |
|------|------|
| **정의** | 단일 클래스/메서드를 외부 의존성 없이 격리하여 검증 |
| **도구** | JUnit 5 + Mockito |
| **속도** | 매우 빠름 (ms 단위) |
| **장점** | 빠른 피드백, 높은 격리성, CI 친화적 |
| **단점** | Mock 과다 시 "실제 동작"과 괴리. JPA 쿼리/트랜잭션/AOP 검증 불가 |
| **적합** | 순수 비즈니스 로직, 유틸리티, 검증 규칙 |

```java
// 예시: Mockito 기반 UserService 단위 테스트
@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService userService;

    @Test
    void 중복_이메일_409() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(true);
        assertThrows(UserException.class, () ->
            userService.register("a@b.com", "pw", "nick"));
    }
}
```

### 1.2 슬라이스 테스트 (Slice Test)

| 항목 | 내용 |
|------|------|
| **정의** | 특정 레이어(JPA, Web MVC 등)만 로드하여 검증 |
| **도구** | `@DataJpaTest`, `@WebMvcTest` |
| **속도** | 빠름 (1~3초) |
| **장점** | 전체 컨텍스트 없이 레이어별 정확한 검증. 쿼리 정합성, 컨트롤러 바인딩 검증 |
| **단점** | 레이어 간 통합 문제 미검출. Service 의 @Transactional 등 AOP 미동작 |
| **적합** | Repository JPQL 쿼리, Controller 요청/응답 매핑 |

```java
// 예시: @DataJpaTest 기반 Repository 쿼리 검증
@DataJpaTest
class FollowRepositoryTest {
    @Autowired FollowRepository followRepository;
    @Autowired TestEntityManager em;

    @Test
    void 활성_팔로우만_조회() { ... }
}
```

### 1.3 통합 테스트 (Integration Test)

| 항목 | 내용 |
|------|------|
| **정의** | 전체 Spring 컨텍스트 로드 + 실제 DB + AOP/트랜잭션 전체 경로 검증 |
| **도구** | `@SpringBootTest` + H2 (또는 Testcontainers PostgreSQL) |
| **속도** | 느림 (5~15초, 컨텍스트 캐싱으로 2회차부터 빠름) |
| **장점** | 실제 운영과 가장 가까운 검증. JPA 쿼리 + @Transactional + @Retryable + AOP 모두 동작 |
| **단점** | 느림, 실패 원인 특정 어려울 수 있음, 테스트 데이터 관리 필요 |
| **적합** | cross-aggregate 비즈니스 로직, 보안 필터 체인, 세션 관리 |

```java
// 예시: @SpringBootTest 기반 FollowService 통합 테스트
@SpringBootTest
@Import(TestSessionConfig.class)
class FollowServiceTest {
    @Autowired FollowService followService;
    // 실제 H2 DB + JPA + @Retryable AOP 전체 경로
}
```

### 1.4 End-to-End 테스트 (E2E)

| 항목 | 내용 |
|------|------|
| **정의** | 실제 서버를 기동하고 HTTP 요청으로 전체 흐름 검증 |
| **도구** | `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `TestRestTemplate`, 또는 Shell curl 스크립트 |
| **속도** | 가장 느림 (서버 부팅 포함) |
| **장점** | 실제 사용자 시나리오 재현. CSRF, 세션 쿠키, HTTP 상태 코드 정확한 검증 |
| **단점** | 매우 느림, 환경 의존적, 디버깅 어려움 |
| **적합** | 인증 흐름 E2E, IDOR 방어 검증, API 계약 확인 |

```bash
# 예시: Shell E2E 테스트 (user.sh)
curl -s -w "%{http_code}" -X POST "$BASE_URL/api/v1/user" \
  -H "Content-Type: application/json" \
  -d '{"email":"a@b.com","password":"pw","nickname":"nick"}'
# → 201
```

## 2. 방법론 비교 매트릭스

| 기준 | 단위 | 슬라이스 | **통합** | E2E (Shell) |
|------|------|---------|---------|-------------|
| 속도 | ★★★★★ | ★★★★ | ★★★ | ★★ |
| 실제 동작 반영도 | ★★ | ★★★ | ★★★★★ | ★★★★★ |
| 격리성 | ★★★★★ | ★★★★ | ★★★ | ★ |
| JPA 쿼리 검증 | ❌ | ✅ | ✅ | ✅ |
| @Transactional 검증 | ❌ | ❌ | ✅ | ✅ |
| @Retryable AOP | ❌ | ❌ | ✅ | ✅ |
| 보안 필터 체인 | ❌ | 부분 | ✅ | ✅ |
| 세션/쿠키/CSRF | ❌ | ❌ | ✅ (MockMvc) | ✅ |
| CI 소요 시간 | < 1s | 1~3s | 5~15s | 10~30s |
| Mock 관리 비용 | 높음 | 중간 | 낮음 | 없음 |

## 3. 선택한 전략과 근거

### 3.1 주력: `@SpringBootTest` 통합 테스트

**이 프로젝트에서 통합 테스트를 주력으로 선택한 이유:**

1. **cross-aggregate 후조건이 많다**
   - `UserService.register()` → User INSERT + FollowCount(0,0) 초기행 생성
   - `UserService.delete()` → Follow/FollowCount/Post cascade 삭제
   - `FollowService.follow()` → Follow INSERT + FollowCount 원자적 UPDATE (user_id 오름차순)
   - 이러한 후조건은 Mock 기반 단위 테스트로 검증할 수 없다. 실제 JPA + @Transactional 이 필요.

2. **동시성 방어 로직이 핵심이다**
   - `@Retryable(TransientDataAccessException.class)` — Spring AOP 프록시가 동작해야 재시도
   - `@Lock(PESSIMISTIC_WRITE)` — 실제 DB 행 잠금이 필요
   - `DataIntegrityViolationException` catch → 409 변환 — 실제 UNIQUE 위반이 발생해야 검증 가능

3. **Mock 과다 사용의 폐해**
   - Service 가 Repository 4개 + PasswordEncoder 1개를 의존 → Mock 5개 세팅
   - `when().thenReturn()` 체인이 테스트 코드의 50%를 차지
   - **Mock 이 실제 JPA 동작(flush, dirty checking, cascade)을 반영하지 못해 "테스트는 통과하지만 실제로는 실패"하는 케이스 발생 위험**

4. **H2 in-memory DB 로 속도 충분**
   - 전체 79 테스트가 **10초 이내** 완료 (Spring 컨텍스트 캐싱 활용)
   - 단위 테스트 대비 속도 차이가 CI 에서 유의미하지 않음

### 3.2 보조: MockMvc 통합 테스트 (Auth 흐름)

**MockMvc 를 Auth 테스트에 선택한 이유:**

1. **Spring Security 필터 체인 전체를 통과해야 의미 있다**
   - `RestAuthenticationFilter` → `SessionAuthenticationStrategy` → `RestAuthSuccessHandler`
   - `TokenVersionFilter` → `AuthController.me()`
   - 이 파이프라인은 `@WebMvcTest` 로는 필터 체인이 불완전하고, Service 직접 호출로는 검증 불가

2. **`springSecurity()` + `MockMvc` 조합이 가장 효율적**
   - `@SpringBootTest` + `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())`
   - HTTP 요청 → 필터 체인 → 컨트롤러 → DB 전체 경로를 서버 기동 없이 검증
   - `csrf()` 헬퍼, `MockHttpSession` 으로 세션 전달 등 테스트 유틸이 풍부

3. **E2E (Shell) 와의 역할 분리**
   - Shell: 실제 HTTP, 실제 쿠키, 실제 CSRF 토큰 → "클라이언트 관점" 계약 검증
   - MockMvc: 내부 필터 동작, tokenVersion 불일치, 세션 고정 방어 → "서버 내부" 동작 검증

### 3.3 보조: Shell E2E 테스트

**Shell 스크립트를 E2E 로 유지하는 이유:**

1. **실제 HTTP 통신 + 쿠키/CSRF 흐름을 가장 정확하게 검증**
2. **개발자가 터미널에서 바로 실행 가능** — 별도 테스트 프레임워크 불필요
3. **시나리오 기반 문서 역할** — API 사용법 예시로도 활용 가능
4. **멱등성 보장** — `$(date +%s)` suffix 로 반복 실행 가능

### 3.4 사용하지 않은 방법과 이유

| 방법 | 미사용 이유 |
|------|------------|
| **Mockito 단위 테스트** | cross-aggregate 후조건(FollowCount, cascade), JPA flush/dirty checking, @Retryable AOP 를 검증 불가. Mock 세팅 비용 대비 신뢰도 낮음 |
| **`@DataJpaTest` 슬라이스** | Repository 쿼리는 통합 테스트에서 자연스럽게 검증됨. 별도 슬라이스로 분리할 만큼 복잡한 커스텀 쿼리가 적음 |
| **`@WebMvcTest` 슬라이스** | Security 필터 체인이 불완전하게 로드됨. `@SpringBootTest` + MockMvc 가 필터 포함 전체 경로를 검증 |
| **Testcontainers (PostgreSQL)** | 현재는 H2 로 충분. 부분 인덱스 등 PostgreSQL 전용 기능 테스트가 필요해지면 도입 검토 |
| **`@SpringBootTest(RANDOM_PORT)` + TestRestTemplate** | MockMvc 로 동일한 검증 가능하면서 서버 부팅 오버헤드 없음 |

## 4. 테스트 설계 원칙

### 4.1 데이터 격리

```java
@BeforeEach
void setUp() {
    // 의존 순서대로 삭제 (FK 제약 없지만 논리적 순서 유지)
    followRepository.deleteAllInBatch();
    postRepository.deleteAllInBatch();
    followCountRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
}
```

- `@Transactional` 롤백 **미사용** — save() 의 `DataIntegrityViolationException` 이 flush 시점에만 발생하므로 테스트 트랜잭션에 묶이면 실제 DB 상태 검증 불가
- `deleteAllInBatch()` 로 수동 초기화 — 의존 역순(follow → post → follow_count → user)

### 4.2 테스트 구조

```
@SpringBootTest
@Import(TestSessionConfig.class)           // Redis 없이 in-memory 세션
@DisplayName("FollowService 통합 테스트")
class FollowServiceTest {

    @Nested @DisplayName("follow()")       // 메서드 단위 그룹핑
    class FollowMethod {
        @Test @DisplayName("정상 팔로우")   // 한글 시나리오명
        void 정상_팔로우() { ... }

        @Test @DisplayName("셀프 팔로우")
        void 셀프_팔로우() { ... }
    }
}
```

| 규칙 | 근거 |
|------|------|
| `@Nested` 로 메서드별 그룹핑 | IDE 트리뷰에서 시나리오 한 눈에 확인 |
| `@DisplayName` 한글 | 테스트 실패 시 의도가 바로 드러남 |
| 메서드명도 한글 | Java 26 유니코드 식별자 지원. 가독성 극대화 |
| 한 테스트 = 한 시나리오 | 실패 시 원인 특정 용이 |

### 4.3 검증 패턴

```java
// 1. 정상 경로: 반환값 + 부수효과(DB 상태) 검증
@Test
void 팔로우_성공() {
    Follow follow = followService.follow(alice.getId(), bob.getId());
    assertThat(follow.isDeleted()).isFalse();                    // 반환값
    assertThat(followService.getFollowCount(bob.getId())         // 부수효과
        .followersCount()).isEqualTo(1);
}

// 2. 예외 경로: 예외 타입 + ErrorType + 메시지 검증
@Test
void 셀프_팔로우() {
    assertThatThrownBy(() -> followService.follow(alice.getId(), alice.getId()))
        .isInstanceOf(FollowException.class)
        .hasMessageContaining("자기 자신을");
}

// 3. 상태 불변 검증: 실패 후 데이터 오염 없음 확인
@Test
void 중복_팔로우_카운트_불변() {
    followService.follow(alice.getId(), bob.getId());
    assertThatThrownBy(() -> followService.follow(alice.getId(), bob.getId()));
    assertThat(followService.getFollowCount(bob.getId())
        .followersCount()).isEqualTo(1);  // 2가 아닌 1
}
```

### 4.4 MockMvc Auth 테스트 패턴

```java
@SpringBootTest
@TestPropertySource(properties = "app.rate-limit.ip-requests-per-minute=10000")
class AuthFlowIntegrationTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())   // 전체 필터 체인 활성화
            .build();
        tokenVersionFilter.evict(testUser.getId());  // Caffeine 캐시 격리
    }

    private MockHttpSession loginAndGetSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(APPLICATION_JSON)
            .content(loginBody(EMAIL, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void tokenVersion_불일치_401() throws Exception {
        MockHttpSession session = loginAndGetSession();
        // DB tokenVersion 강제 증가 + Caffeine evict
        user.changePassword(encoder.encode("new"));
        userRepository.saveAndFlush(user);
        tokenVersionFilter.evict(user.getId());
        // 다음 요청은 401
        mockMvc.perform(get("/api/v1/auth/me").session(session))
            .andExpect(status().isUnauthorized());
    }
}
```

| 규칙 | 근거 |
|------|------|
| `@TestPropertySource(rate-limit=10000)` | 동일 IP(127.0.0.1) 반복 요청으로 429 방지 |
| `tokenVersionFilter.evict()` 매 테스트 | Caffeine 30초 TTL 캐시 잔류 방지 |
| `loginAndGetSession()` 헬퍼 | 세션 확보 로직 중복 제거 |
| `MockHttpSession` 전달 | `.session(session)` 으로 인증 상태 유지 |

## 5. 테스트 커버리지 현황

### 5.1 Java 통합 테스트 (79 tests)

| 클래스 | 시나리오 수 | 대상 |
|--------|-----------|------|
| `FollowServiceTest` | 30 | follow/unfollow/getFollowers/getFollowings/isFollowing/getFollowCount |
| `UserServiceTest` | 36 | register/getByXxx/getAll/update/changePassword/delete(cascade) |
| `AuthFlowIntegrationTest` | 12 | Login(6)/Me(4)/Logout(2) |
| `ApplicationTests` | 1 | contextLoads |

### 5.2 Shell E2E 테스트 (54 steps)

| 스크립트 | Steps | 검증 |
|----------|-------|------|
| `user.sh` | 23 | 가입 검증/중복/IDOR/비번변경/삭제 cascade |
| `auth.sh` | 7 | 로그인/me/로그아웃 |
| `post.sh` | 9 | CRUD + 비인증 방어 |
| `follow.sh` | 15 | 팔로우/중복/셀프/카운트/재팔로우 |

### 5.3 미커버 영역

| 영역 | 이유 | 향후 계획 |
|------|------|-----------|
| 동시성 race (DataIntegrityViolation) | H2 단일 스레드로는 동시 요청 재현 불가 | JMeter/k6 부하 테스트 또는 Testcontainers PostgreSQL |
| Rate limit 429 | 20회 반복 요청은 테스트 시간 부담 | `RateLimitFilterTest` 단위 테스트 분리 |
| AuthEventListener 로그 마스킹 | Logger 캡처 필요 | Logback `ListAppender` 별도 테스트 |
| 동시 세션 제한 (maxSessions=3) | in-memory 스텁이 인덱싱 미지원 | Redis Testcontainers 도입 시 |

## 6. 테스트 인프라

### 6.1 TestSessionConfig

```java
@TestConfiguration
public class TestSessionConfig {
    @Bean
    public FindByIndexNameSessionRepository<MapSession> findByIndexNameSessionRepository() {
        return new InMemoryFindByIndexNameSessionRepository();
    }
}
```

- Redis 없이 `SpringSessionBackedSessionRegistry` 를 빈으로 등록
- `findByIndexNameAndIndexValue` 는 빈 map 반환 → 동시 세션 제한 미동작 (테스트 환경 수용)

### 6.2 프로필 설정

| 항목 | test (application.yaml) |
|------|------------------------|
| 프로필 | `dev` |
| DB | H2 in-memory |
| Session | `store-type: none` |
| Flyway | `enabled: false` |
| DDL | `create-drop` (Hibernate) |

## 7. 향후 개선 로드맵

| 단계 | 도입 | 효과 |
|------|------|------|
| 1 | Testcontainers PostgreSQL | 부분 인덱스, JSONB 등 PostgreSQL 전용 기능 테스트 |
| 2 | RateLimitFilter 단위 테스트 | Caffeine 캐시 만료/초과 시나리오 격리 검증 |
| 3 | AuthEventListener 로그 테스트 | 마스킹 규칙 회귀 방지 |
| 4 | API 계약 테스트 (Spring Cloud Contract / Pact) | 프론트-백엔드 계약 변경 감지 |
| 5 | Playwright E2E | 프론트엔드 포함 전체 흐름 |
