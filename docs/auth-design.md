# Authentication / Authorization Design Recommendation

> 본 문서는 이 프로젝트(Spring Boot SNS + Next.js)의 사용자 인증·인가 설계 권장안이다.
> 결론은 **"Session-Primary, JWT-Auxiliary"** 하이브리드이며, 그 근거와 적용 범위를 정리한다.

---

## 1. 현재 상태 요약

| 항목 | 구현 | 적용 일자 |
|------|------|-----------|
| 메커니즘 | Spring Security 6 + **Spring Session Redis** (`RedisIndexedSessionRepository`) | 04-13 |
| 비밀번호 해싱 | Argon2id (`Argon2Password4jPasswordEncoder` — password4j 1.8.2) | 04-12 |
| Principal | 커스텀 `AuthUser`(id/email/nickname/tokenVersion, `Serializable`) — `UserDetailsServiceImpl` 가 로드 | 04-13 |
| 세션 저장소 | **Redis** (`spring-boot-starter-session-data-redis`, namespace `sns`) | 04-13 |
| 세션 레지스트리 | `SpringSessionBackedSessionRegistry` — 다중 인스턴스 세션 관리 | 04-13 |
| 동시 세션 제어 | `maximumSessions(3)` + `CompositeSessionAuthenticationStrategy` — 초과 시 최오래 세션 만료 | 04-13 |
| 세션 만료 | Sliding 30분 (`spring.session.timeout`) + Absolute 24시간 (`AbsoluteSessionTimeoutFilter`) | 04-13 |
| 세션 고정 방어 | `ChangeSessionIdAuthenticationStrategy` — 로그인 시 세션 ID 재발급 | 04-11 |
| CSRF | `CookieCsrfTokenRepository` + **`XorCsrfTokenRequestAttributeHandler`** (BREACH 완화) + `CsrfCookieFilter` | 04-12 |
| 로그인 | `POST /api/auth/login` — `RestAuthenticationFilter` (JSON body, `MediaType` 호환 검증) | 04-13 |
| 비밀번호 변경 | `PUT /api/user/{id}/password` — 현재 비밀번호 재확인 + `User.changePassword()` (tokenVersion 자동 증가) + Redis 세션 즉시 삭제 | 04-13 |
| 프로필 수정 | `PUT /api/user/{id}` — **nickname만 변경** (password 필드 제거, 비밀번호 변경 백도어 차단) | 04-13 |
| token version | `User.tokenVersion` + `AuthUser.tokenVersion` → `/api/auth/me` 에서 비교, 불일치 시 세션 무효화 + Redis 즉시 삭제 (fallback) | 04-13 |
| 현재 사용자 | `GET /api/auth/me` (`@AuthenticationPrincipal AuthUser`) + null 방어 + tokenVersion 검증 | 04-13 |
| 로그아웃 | `POST /api/auth/logout` (Spring Security `logout()` DSL) | 04-11 |
| 인가 (IDOR 방어) | `requireOwnership(authUser, id)` — PUT/DELETE /api/user/{id} 본인 검증 | 04-13 |
| Rate limiting | `RateLimitFilter` (Bucket4j + **Caffeine 캐시**) — IP 기반 분당 20회, XFF 불신, 개별 eviction | 04-13 |
| Audit log | `AuthEventListener` — 로그인 성공/실패/로그아웃 + **세션 생성/삭제/만료** 이벤트 감사 로깅 (세션 ID 축약) | 04-13 |
| 운영 cookie | `application-prod.yaml` — secure, http-only, same-site lax | 04-12 |
| HTTPS 강제 | `SecurityConfig` — prod 프로필에서만 `requiresChannel().requiresSecure()` | 04-12 |
| CSP 헤더 | `Content-Security-Policy: default-src 'self'; script-src 'self'` | 04-12 |
| H2 콘솔 제한 | `SecurityConfig` — dev 프로필에서만 접근 허용 (`AuthorizationDecision`) | 04-13 |
| 프론트 ↔ 백 | Next.js rewrites 로 same-origin 프록시, 쿠키 + XSRF-TOKEN 자동 첨부 | 04-11 |
| 인증 예외 | `unauthorizedEntryPoint` 가 401 통일 + `AuthenticationException` 전체 catch | 04-11 |
| 타이밍 공격 방어 | `UserDetailsServiceImpl` 가 더미 hash `passwordEncoder.matches` 호출 | 04-11 |
| 설정 외부화 | `AppSessionProperties`, `RateLimitProperties` — `@ConfigurationProperties` record | 04-13 |
| Role 기반 인가 | `Role` enum (`USER`, `ADMIN`) + `AuthUser.getAuthorities()` + `@EnableMethodSecurity` + `/api/admin/**` hasRole | 04-13 |

---

## 2. 핵심 요구사항 + 위협 모델

### 요구사항 (현재 + 단기)
1. 회원가입/로그인/로그아웃
2. 게시글/댓글 등 본인 소유 리소스의 mutation 권한 체크
3. 브라우저 단일 클라이언트 (Next.js)
4. 비로그인 사용자도 일부 read-only 접근 가능 (피드 열람, 프로필 조회)

### 요구사항 (중장기, 가능성)
5. 모바일 앱 (iOS/Android) — 동일 백엔드 호출
6. OAuth 소셜 로그인 (Google, GitHub, Apple 등)
7. Admin / Role 기반 권한
8. 토큰 기반 외부 API 통합 (예: 제3자 자동화 클라이언트)
9. 이메일 인증 / 비밀번호 재설정 링크
10. Refresh / Remember-Me ("로그인 유지")

### 위협 모델
| 위협 | 대응 위치 |
|------|-----------|
| 자격증명 탈취 (network sniffing) | HTTPS (운영) |
| 자격증명 brute force | rate limiting (미구현 — TODO), 계정 잠금 |
| 세션 탈취 (cookie theft via XSS) | `HttpOnly` 쿠키, CSP, 입력 sanitization |
| 세션 고정 (session fixation) | 로그인 직후 session ID 재발급 ✅ |
| CSRF | CookieCsrfTokenRepository + X-XSRF-TOKEN ✅ |
| 타이밍 공격 (계정 enumeration) | UserDetailsService 더미 matches ✅ |
| 토큰 탈취 후 무한 사용 (JWT 의 본질적 약점) | refresh + 짧은 access TTL, revocation list |
| 비밀번호 db 노출 | Argon2 (memory-hard) ✅ |
| Click-jacking | `frameOptions.deny()` (운영) ✅ |
| Mass assignment | DTO + Bean Validation ✅ |
| 권한 우회 (IDOR) | Service 레벨 작성자 검증 (PostService.isAuthor) ✅ |

---

## 3. 인증 방식 비교

### 3.1 Session (Cookie + Server-Side State)

| 장점 | 단점 |
|------|------|
| 즉시 revocation 가능 (서버에서 세션 삭제) | 서버 상태 필요 (메모리/DB/Redis) |
| HttpOnly 쿠키 → JS 노출 차단 → XSS 토큰 탈취 차단 | 쿠키 = same-origin 제약 (cross-domain 시 불편) |
| 작은 cookie 크기 | 서버 확장 시 sticky session or 외부 저장 필요 |
| CSRF 필요 (대응 가능) | 모바일/서드파티에는 불편 |
| 구현 단순, Spring Security 기본 지원 | |

### 3.2 JWT (Stateless Token)

| 장점 | 단점 |
|------|------|
| 서버 무상태 → 수평 확장 용이 | **즉시 revocation 어려움** (블랙리스트 별도 관리 필요) |
| 모바일/서드파티 친화적 | 토큰 크기 큼 (cookie/header 부담) |
| Cross-domain 자유 | 키 회전, secret 관리 복잡 |
| 마이크로서비스 간 전파 용이 | 토큰 탈취 시 만료 전까지 무력화 불가 |
| OAuth 와 자연스럽게 통합 | refresh token 별도 관리 필요 |

### 3.3 OAuth2 / OIDC (외부 IdP)

- 사용자 식별을 외부 IdP(Google, GitHub 등)에 위임
- 자체 회원가입/로그인 유지의 부담 제거
- Spring Security `oauth2Login()` 으로 매우 간단히 적용 가능
- **본 프로젝트에는 자체 가입 + 향후 OAuth 추가 구도가 적합**

---

## 4. 권장: Session-Primary, JWT-Auxiliary

### 핵심 결론

> **웹 클라이언트는 Session, 비-웹 채널은 목적 한정 JWT** 로 분리한다.
> Session 은 일상적 인증의 기본 매체, JWT 는 특정 시나리오 한정 도구다.

### 근거

1. **단일 웹 프론트 (Next.js) + same-origin 프록시 구조** 에서 세션은 가장 단순하고 안전하다.
2. **즉시 revocation** 이 SNS 의 핵심 요구다 (계정 정지, 비밀번호 변경 시 모든 세션 무효화). JWT 만으로는 별도 블랙리스트 인프라가 필요하다.
3. **HttpOnly 쿠키** 는 XSS 발생 시 토큰 탈취를 원천 차단한다. JWT 를 `localStorage` 에 두면 XSS 한 번에 모든 토큰이 노출된다.
4. **운영 복잡도가 낮다**. 키 회전, 발급 키 분리, refresh 흐름, 토큰 클럭 스큐 등 JWT 운영 비용이 단일 백엔드 환경에서는 과한 비용이다.
5. **JWT 는 필요할 때 추가**. 모바일 앱이 생기거나 외부 통합이 필요해지면 그 시점에 한정 도입하면 된다.

### 사용 범위 매트릭스

| 채널 / 시나리오 | 메커니즘 | 저장 위치 | TTL |
|----------------|---------|-----------|-----|
| **Next.js 웹 클라이언트** | Session (`JSESSIONID`) | HttpOnly Cookie | 30분 슬라이딩 갱신 |
| **이메일 인증 링크** | 단기 JWT | URL token | 24시간 |
| **비밀번호 재설정 링크** | 단기 JWT (1회용) | URL token | 30분 |
| **모바일 앱 (장래)** | JWT (access + refresh) | 모바일 keychain | access 15분 / refresh 14일 |
| **외부 API 클라이언트 (장래)** | API key 또는 OAuth2 client credentials | 별도 관리 콘솔 | 영구/회전 |
| **CI 봇 / 어드민 자동화 (장래)** | API key | 별도 관리 콘솔 | 영구/회전 |

---

## 5. 인증 흐름 (Auth Flow)

### 5.1 웹 로그인 (Session)

```
[Browser]                            [Spring Boot]
   │                                     │
   │ 1. GET / (페이지 로드)              │
   │ ──────────────────────────────────► │
   │                                     │ — CsrfCookieFilter 가 XSRF-TOKEN 발급
   │ ◄──────────────────── Set-Cookie    │
   │                                     │
   │ 2. POST /api/auth/login             │
   │    + X-XSRF-TOKEN header            │
   │ ──────────────────────────────────► │
   │                                     │ — AuthenticationManager.authenticate()
   │                                     │ — UserDetailsService 로 사용자 로드
   │                                     │ — PasswordEncoder.matches()
   │                                     │ — SessionAuthenticationStrategy.onAuthentication() (세션 ID 재발급)
   │                                     │ — securityContextRepository.saveContext()
   │ ◄────────── 200 + JSESSIONID cookie │
   │                                     │
   │ 3. POST /api/post (글쓰기)          │
   │    + JSESSIONID + X-XSRF-TOKEN      │
   │ ──────────────────────────────────► │
   │                                     │ — SecurityContextHolderFilter 가 세션에서 SecurityContext 복원
   │                                     │ — @AuthenticationPrincipal AuthUser 주입
   │ ◄──────────────────── 201 Created   │
```

### 5.2 단기 토큰 (이메일/비밀번호 재설정 — 향후)

```
[Backend]                                [Email service]
   │ generate signed JWT(sub=userId,     │
   │   purpose=password_reset,            │
   │   exp=30min,                         │
   │   jti=uuid)                          │
   │ store jti → DB (1회용 사용 추적)    │
   │ → email link with token             │
   ├────────────────────────────────────► │
   │
[Browser]
   │ GET /reset?token=...                │
   │ ──────────► [Backend]               │
   │             — verify signature      │
   │             — verify exp / purpose  │
   │             — verify jti unused     │
   │             — mark jti used         │
   │             — show password form    │
```

이 흐름은 세션과 무관하게 작동한다. 토큰은 단일 목적, 단기, 1회용이라 JWT 의 무상태성이 자연스러운 적합점이다.

### 5.3 로그아웃

```
POST /api/auth/logout
  → Spring Security logout DSL
  → SecurityContextLogoutHandler: SecurityContextHolder.clearContext()
  → SecurityContextHolderStrategy.clearContext()
  → CookieClearingLogoutHandler: JSESSIONID 만료
  → InvalidateHttpSessionLogoutHandler: session.invalidate()
  → 204 No Content
```

### 5.4 비밀번호 변경 시 전체 세션 무효화 (권장)

비밀번호 변경 또는 계정 정지 시 다른 디바이스의 세션도 모두 끊어야 한다. 두 가지 방식:

**방식 A: 사용자별 token version**
- `User.tokenVersion` 컬럼 추가
- 세션 attribute 에 로그인 시점의 tokenVersion 저장
- 매 요청마다 세션의 tokenVersion ↔ User 의 현재 tokenVersion 비교
- 다르면 세션 무효화 + 401

**방식 B: 사용자별 세션 인덱스**
- Spring Session + `FindByIndexNameSessionRepository` 사용
- 사용자 ID 로 활성 세션을 찾아 일괄 invalidate
- Spring Session + Redis 권장

> 본 프로젝트는 학습용이라 **방식 A** 를 권장 (구현이 단순). 프로덕션에서 다중 인스턴스가 되면 방식 B 로 이전.

---

## 6. 인가 (Authorization)

### 6.1 권한 모델

현재: 단일 사용자 권한 (작성자 본인 vs 그 외).

권장 단계:

**Phase 1 (현재)** — 작성자 본인 검증만
- `Post.isAuthor(userId)` 도메인 메서드로 처리
- Service 레이어에서 검증, 위반 시 `PostException.forbidden`

**Phase 2 (Admin 필요 시)** — Role enum 도입
- `User.role: Role { USER, ADMIN }`
- `AuthUser.getAuthorities()` 가 `[ROLE_USER]` 또는 `[ROLE_USER, ROLE_ADMIN]` 반환
- `@PreAuthorize("hasRole('ADMIN')")` 메서드 보안 활성화
- SecurityConfig 에서 `.requestMatchers("/api/admin/**").hasRole("ADMIN")`

**Phase 3 (세분 권한 필요 시)** — Permission 기반 또는 Spring ACL
- 필요해질 때 도입. 보통 SNS 는 작성자 + Admin 두 단계로 충분.

### 6.2 인가 위치 원칙

| 검증 종류 | 위치 | 이유 |
|----------|------|------|
| 인증 여부 (`anyRequest().authenticated()`) | SecurityFilterChain | URL 단위 일괄 처리 |
| Role | SecurityFilterChain `.hasRole()` 또는 메서드 `@PreAuthorize` | 선언적, 컨트롤러/서비스 진입 차단 |
| 작성자 본인 검증 (도메인 권한) | **Service 레이어** | 도메인 불변식. URL 패턴으로 표현 불가능 |
| 입력 값 검증 | Controller (`@Valid`) + Entity 불변식 | 표현/도메인 두 계층 방어 |

### 6.3 본 프로젝트 권장 매트릭스

| 엔드포인트 | 인가 |
|-----------|------|
| `POST /api/user` (회원가입) | Public |
| `POST /api/auth/login` | Public |
| `POST /api/auth/logout` | Authenticated |
| `GET /api/auth/me` | Authenticated |
| `GET /api/post`, `/api/post/{id}` | Public (열람) |
| `POST /api/post` | Authenticated |
| `PUT/DELETE /api/post/{id}` | Authenticated + 작성자 본인 |
| `GET /api/user`, `/api/user/{id}` | Public (프로필) |
| `PUT/DELETE /api/user/{id}` | Authenticated + 본인 |
| `/h2-console/**` | Dev profile only |
| (장래) `/api/admin/**` | Authenticated + ROLE_ADMIN |

---

## 7. Session 사용 범위

### 7.1 무엇을 세션에 담는가

| 항목 | 담아도 OK | 이유 |
|------|----------|------|
| `SecurityContext` (Authentication) | ✅ | Spring Security 표준 |
| 사용자 ID | ✅ (Principal 안에) | AuthUser.id |
| 사용자 email | ✅ (Principal 안에) | 토큰성 식별자가 아니므로 OK |
| 사용자 nickname | ❌ | 자주 변경됨 → 매번 DB 조회 또는 짧은 cache |
| 권한 (Role) | ✅ (Principal 안에 — 짧은 TTL 권장) | 권한 변경 시 재로그인 강제 또는 token version 으로 무효화 |
| 사용자 프로필 사진 URL | ❌ | DB / CDN 에서 fetch |
| 장바구니 / 작성중 게시글 임시본 | ❌ | DB 또는 클라이언트 localStorage |
| Flash message (`addFlashAttribute`) | ✅ (1회성) | Spring 표준 |
| token version | ✅ (다중 디바이스 무효화 시) | |
| 비밀번호 / 비밀번호 hash | ❌ | 절대 금지 |
| 결제 정보 / 카드 번호 | ❌ | 절대 금지 |
| **임의의 도메인 데이터** | ❌ | 세션은 인증 컨텍스트만 |

원칙: **세션은 "이 요청을 보낸 주체가 누구인가" 만 담는다**. 그 외의 모든 도메인 데이터는 DB 가 단일 진리원이다.

### 7.2 세션 저장소

| 환경 | 저장소 | 비고 |
|------|--------|------|
| Dev (현재) | 서버 메모리 (Tomcat in-memory) | 재기동 시 초기화 — OK |
| Prod (단일 인스턴스) | 서버 메모리 | 단순. 재기동 시 사용자 재로그인 |
| Prod (다중 인스턴스) | **Spring Session + Redis** | sticky 불필요, 일괄 invalidate 용이 |

이전 시점: 트래픽이 단일 인스턴스의 한계를 넘거나, 다중 인스턴스/zero-downtime deploy 가 필요해질 때.

### 7.3 세션 TTL

권장값:
- **idle timeout**: 30분 (마지막 요청 이후)
- **absolute timeout**: 7일 (로그인 시점부터)
- **remember-me**: 14일 (별도 토큰 — 아래)

구현: `application.yaml` 의 `server.servlet.session.timeout=30m` + 절대 만료는 직접 attribute 로 관리.

### 7.4 Remember-Me ("로그인 유지") — 향후

방식 1: Spring Security `rememberMe()` DSL — DB 에 토큰 저장 (`PersistentTokenRepository`).
방식 2: 별도 **long-lived refresh token** (DB) + 짧은 세션 TTL.

권장: 방식 1 (Spring 표준).

---

## 8. JWT 사용 범위 (auxiliary)

### 8.1 JWT 가 적합한 경우 (이 프로젝트 한정)

| 시나리오 | 토큰 종류 | TTL | 검증 비용 |
|---------|----------|-----|-----------|
| 이메일 검증 링크 | signed JWT | 24h | sig + jti 1회 사용 |
| 비밀번호 재설정 링크 | signed JWT | 30m | sig + jti 1회 사용 + 사용 후 즉시 무효화 |
| 모바일 앱 access token (장래) | JWT | 15m | sig + DB user 활성 여부 |
| 모바일 앱 refresh token (장래) | opaque or JWT | 14d | DB 매칭 |

### 8.2 JWT 가 부적합한 경우

- 웹 브라우저 로그인 인증 매체 (→ Session 사용)
- 즉시 무효화가 필요한 권한 (→ Session 또는 짧은 TTL + 블랙리스트)
- 큰 페이로드 (사용자 프로필 등)

### 8.3 JWT 라이브러리 권장

- `io.jsonwebtoken:jjwt-api:0.12.x` + `jjwt-impl` + `jjwt-jackson`
- 또는 Spring Security `oauth2-resource-server` 의 JWT 지원

### 8.4 키 관리

- **HS256** (대칭) — 단일 백엔드, 단일 검증자에 적합. 본 프로젝트는 이 케이스
- **RS256** (비대칭) — 발급자와 검증자가 분리될 때 (마이크로서비스, OAuth IdP)
- secret 은 환경변수 / Vault / Secret Manager. **코드 / git 절대 금지**
- **키 회전 주기**: 90일. 회전 중에는 두 키 동시 검증 허용

---

## 9. 비밀번호 / 자격증명 관리

### 9.1 PasswordEncoder 구현

| 항목 | 값 |
|------|------|
| 구현 클래스 | `Argon2Password4jPasswordEncoder` (Spring Security 7.x 권장) |
| 라이브러리 | `com.password4j:password4j:1.8.2` |
| 설정 파일 | `src/main/resources/psw4j.properties` |
| 이전 구현 | `Argon2PasswordEncoder` (BouncyCastle) — **Spring Security 7 에서 @Deprecated** |
| 호환성 | 두 구현 모두 Argon2id 표준 포맷 (`$argon2id$v=19$m=...`) — 기존 DB hash 와 호환 |

#### 왜 Argon2id 인가 — 알고리즘 선택 사유

비밀번호 해싱 알고리즘은 **BCrypt → SCrypt → Argon2** 순서로 발전해왔다. 각각의 특성과 본 프로젝트에서 Argon2id 를 선택한 근거:

| 알고리즘 | 특징 | 한계 |
|---------|------|------|
| **BCrypt** | CPU-hard. 20년+ 검증. Spring Security 기본 | 메모리 소비가 적어 GPU/ASIC 공격에 상대적으로 취약 |
| **SCrypt** | CPU + memory-hard. BCrypt 대비 GPU 방어 우위 | 파라미터 튜닝이 복잡 (N, r, p 3개). 일부 구현에 사이드채널 취약점 |
| **Argon2id** | CPU + memory-hard + **side-channel 방어**. 2015 PHC 우승 | 상대적으로 신규. 라이브러리 지원 이력이 짧음 |

**Argon2id 선택 근거:**

1. **PHC (Password Hashing Competition) 2015 우승** — 학술·산업계 검증을 거친 현시점 최강 알고리즘
2. **memory-hard** — GPU/ASIC 병렬 공격 비용을 선형이 아닌 메모리 비례로 증가시켜 대규모 크래킹을 비경제적으로 만듦
3. **Argon2id = Argon2i + Argon2d 결합** — side-channel 공격(timing, cache)과 GPU 공격을 동시에 방어. 서버 환경에서 가장 안전한 변형
4. **OWASP 권장** — OWASP Password Storage Cheat Sheet 에서 Argon2id 를 1순위로 권장
5. **NIST SP 800-63B 부합** — memory-hard function 사용 권고에 부합
6. **Spring Security 7 공식 지원** — `Argon2Password4jPasswordEncoder` 로 1급 지원, 별도 구현 불필요

> **BCrypt 를 선택하지 않은 이유**: BCrypt 는 4KB 고정 메모리만 사용하여 GPU 에서 대량 병렬 실행이 가능하다.
> 현대 GPU (RTX 4090 기준) 로 BCrypt cost=12 에서 초당 약 2만 hash 를 시도할 수 있지만,
> Argon2id (memory=19MB) 에서는 GPU 메모리 제약으로 병렬도가 극적으로 제한된다.

> **SCrypt 를 선택하지 않은 이유**: SCrypt 는 memory-hard 지만 Argon2 대비 파라미터 튜닝이 복잡하고 (N, r, p 세 축을 개별 조정),
> side-channel 방어가 약하며, Spring Security 7 에서 `Argon2Password4jPasswordEncoder` 가 1급 지원되는 반면
> SCrypt 는 BouncyCastle 기반 deprecated 구현만 남아 있다.

#### 왜 password4j 라이브러리인가 — BouncyCastle 에서 교체된 사유

| 비교 항목 | BouncyCastle (`Argon2PasswordEncoder`) | password4j (`Argon2Password4jPasswordEncoder`) |
|----------|---------------------------------------|-----------------------------------------------|
| Spring Security 7 상태 | **@Deprecated** | 권장 (신규 `password4j` 패키지) |
| 라이브러리 크기 | BouncyCastle 전체 (~5.8MB jar) | password4j (~120KB jar) |
| 용도 | 범용 암호화 라이브러리 (TLS, PGP, ...) | 비밀번호 해싱 전용 |
| 의존성 특성 | 너무 큰 범위 — 비밀번호 해싱만 필요한데 전체 암호 라이브러리 의존 | 경량, 단일 목적 |
| 설정 | Java 코드 파라미터 | `psw4j.properties` 외부 설정 (코드 변경 없이 파라미터 튜닝 가능) |
| Argon2 구현 | BouncyCastle 자체 구현 | password4j 자체 구현 (Reference C 구현 포팅) |
| 해시 포맷 호환 | `$argon2id$v=19$m=...` 표준 | `$argon2id$v=19$m=...` 동일 표준 — **기존 DB hash 호환** |

**교체 근거:**

1. **Spring Security 7 에서 기존 클래스 @Deprecated** — 유지보수/보안 패치를 기대할 수 없음
2. **경량 의존성** — BouncyCastle(5.8MB) 대신 password4j(120KB) 로 빌드 크기 절감
3. **외부 설정 지원** — `psw4j.properties` 로 코드 변경 없이 파라미터 튜닝 가능 (운영 중 보안 수준 상향 시 재배포만으로 적용)
4. **DB 호환성 유지** — 동일 Argon2id 표준 포맷이라 기존 비밀번호 hash 가 그대로 동작
5. **단일 목적 라이브러리** — 비밀번호 해싱에만 집중하여 보안 감사 범위가 작고 유지보수 부담 낮음

**psw4j.properties 파라미터:**

| 파라미터 | 값 | 설명 |
|---------|-----|------|
| `hash.argon2.memory` | 19456 (19MB) | 메모리 비용 — Spring Security 5.8 기본값과 동등 |
| `hash.argon2.iterations` | 2 | 시간 비용 (반복 횟수) |
| `hash.argon2.length` | 32 | 해시 출력 길이 (bytes) |
| `hash.argon2.parallelism` | 1 | 병렬도 |
| `hash.argon2.type` | id | Argon2**id** (side-channel + GPU 동시 방어) |
| `hash.argon2.version` | 19 | Argon2 표준 버전 |
| `global.salt.length` | 16 | Salt 길이 (bytes) |

> **주의**: `psw4j.properties` 가 없으면 password4j 가 기동 시 WARN 로그 7건을 출력한다. 동작에 영향은 없으나 로그가 노이즈가 됨.
> 미설정 시 password4j 기본값(`memory=15360`, `salt=64`)은 Spring Security 5.8 기본값(`memory=19456`, `salt=16`)과 다르므로 명시적 설정 필수.

### 9.2 비밀번호 정책 / 관리

| 항목 | 규칙 | 근거 | 상태 |
|------|------|------|------|
| 해싱 알고리즘 | **Argon2id** | PHC 우승, OWASP 1순위, memory-hard | ✅ |
| 평문 노출 | 절대 금지 (로그, 응답, DB 모두) | OWASP 기본 원칙 | ✅ |
| 최소 길이 | **8자** | NIST 800-63B §5.1.1 최소 요구 | ✅ |
| 최대 길이 | **64자** | NIST 800-63B 최소 64자 허용 권장. Argon2 입력 제한 없음 | ✅ |
| 복잡도 강제 | **비적용** (대문자/특수문자 등 강제 안 함) | NIST 800-63B — 복잡도 규칙이 예측 가능한 패턴(`P@ssw0rd!`)을 유도하여 오히려 보안 약화 | ✅ |
| 현재 비밀번호 재확인 | 변경 시 필수 | 세션 탈취 시 비밀번호 변경 방지 | ✅ |
| 현재 비밀번호와 동일 금지 | 새 비밀번호 ≠ 현재 비밀번호 | 무의미한 변경 방지 + tokenVersion 불필요 증가 방지 | 설계 완료 |
| 최근 비밀번호 재사용 금지 | 최근 **3개** 와 동일 금지 | 순환 사용 방지 (NIST는 강제하지 않으나, KISA/금융권 권장) | 설계 완료 |
| 유출 비밀번호 차단 | **Pwned Passwords** API 연동 | NIST 800-63B §5.1.1.2 — 유출 목록 대조 "SHALL" | 설계 완료 |
| 주기적 변경 강제 | **비적용** | NIST 800-63B — 정기 강제 변경은 약한 비밀번호 선택 유도. 유출 시에만 변경 | — |
| 비밀번호 초과 시도 | rate limiting + 계정 잠금 | brute force 방어 | 일부 ✅ (rate limit) |
| 2FA / TOTP | optional | Google Authenticator 호환 | 향후 |

### 9.3 비밀번호 변경 규칙 — 상세 설계

#### 9.3.1 규칙 요약

비밀번호 변경(`PUT /api/user/{id}/password`) 시 다음 규칙을 **순서대로** 검증한다:

```
1. 현재 비밀번호 재확인           → 불일치 시 401 (invalidCredentials)
2. 새 비밀번호 길이 검증          → 8~64자 위반 시 400 (invalidField)
3. 현재 비밀번호와 동일 여부      → 동일 시 400 (samePassword)
4. 최근 3개 비밀번호 재사용 여부  → 일치 시 400 (recentlyUsedPassword)
5. 유출 비밀번호 차단 (optional)  → Pwned Passwords 해당 시 400 (compromisedPassword)
```

#### 9.3.2 비밀번호 이력 관리 (Password History)

**목적**: 사용자가 비밀번호를 A → B → A 로 순환 사용하는 것을 방지.

**설계 근거 — 이력 보관 개수 선택**

| 보관 개수 | 장점 | 단점 |
|----------|------|------|
| 1 (현재만) | 구현 최소 | 순환 사용 방지 불가 |
| **3 (권장)** | 순환 방지 + 저장 부담 적음 | — |
| 5 (금융권) | 강력한 재사용 방지 | SNS 서비스에는 과도 |
| 24 (Windows AD) | 엔터프라이즈 수준 | 과잉. 사용자 불편 극대화 |

> **NIST 800-63B 는 비밀번호 이력 강제를 명시적으로 권장하지 않는다** (주기적 변경 강제와 함께 비권장).
> 그러나 KISA 개인정보보호 가이드와 금융감독원 권고에서는 최근 N개 재사용 금지를 권장한다.
> 본 프로젝트는 **3개**를 채택한다 — SNS 서비스의 보안/UX 균형점.

**데이터 모델**

```java
@Entity
@Table(name = "password_history")
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private User user;

    @Column(nullable = false)
    private String encodedPassword;   // Argon2 해시

    @Column(nullable = false)
    private Instant createdAt;

    protected PasswordHistory() {}

    public PasswordHistory(User user, String encodedPassword) {
        this.user = user;
        this.encodedPassword = encodedPassword;
        this.createdAt = Instant.now();
    }
}
```

**Repository**

```java
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    // 최근 N개 이력 조회 (최신순)
    List<PasswordHistory> findTop3ByUserOrderByCreatedAtDesc(User user);

    // 오래된 이력 정리 (보관 개수 초과분 삭제)
    @Modifying
    @Query("DELETE FROM PasswordHistory ph WHERE ph.user = :user AND ph.id NOT IN "
         + "(SELECT ph2.id FROM PasswordHistory ph2 WHERE ph2.user = :user ORDER BY ph2.createdAt DESC LIMIT 3)")
    void deleteOldEntries(@Param("user") User user);
}
```

**변경 흐름**

```
UserService.changePassword(id, currentRaw, newRaw)
  │
  ├─ 1. validateRawPassword(currentRaw), validateRawPassword(newRaw)
  ├─ 2. passwordEncoder.matches(currentRaw, user.password)  → 실패 시 invalidCredentials
  ├─ 3. currentRaw.equals(newRaw)                           → 동일 시 samePassword
  ├─ 4. passwordHistoryRepository.findTop3ByUser(user)
  │     → 각 이력에 대해 passwordEncoder.matches(newRaw, history.encodedPassword)
  │     → 일치하는 이력 있으면 recentlyUsedPassword
  ├─ 5. (optional) pwnedPasswordsClient.isCompromised(newRaw)
  │     → 유출 목록 해당 시 compromisedPassword
  │
  ├─ user.changePassword(encode(newRaw))  ← tokenVersion 자동 증가
  ├─ passwordHistoryRepository.save(new PasswordHistory(user, user.password 의 이전값))
  ├─ passwordHistoryRepository.deleteOldEntries(user)  ← 3개 초과분 정리
  │
  └─ refreshSecurityContext + invalidateOtherSessions
```

**성능 고려사항**

| 항목 | 영향 | 대응 |
|------|------|------|
| `passwordEncoder.matches()` × 3회 (이력 검증) | 각 ~50ms (Argon2) → 총 ~150ms 추가 | 비밀번호 변경은 드문 작업. 수용 가능 |
| `PasswordHistory` 테이블 크기 | 사용자당 최대 3행. 100만 사용자 = 300만 행 | 인덱스: `(user_id, created_at DESC)` |
| 비밀번호 변경 시 DB 접근 | SELECT(이력 3건) + UPDATE(User) + INSERT(이력) + DELETE(초과분) | 단일 `@Transactional` 로 묶음 |

#### 9.3.3 유출 비밀번호 차단 (Pwned Passwords) — optional

**k-Anonymity 방식** (NIST 800-63B 준수):

```
1. 새 비밀번호의 SHA-1 해시 계산
2. 해시 앞 5자리(prefix)만 haveibeenpwned API 로 전송
3. API 가 해당 prefix 로 시작하는 해시 목록 반환
4. 클라이언트에서 나머지 suffix 를 로컬 비교
→ 비밀번호 원문이 외부로 전송되지 않음
```

```java
// PwnedPasswordsClient (optional)
public boolean isCompromised(String rawPassword) {
    String sha1 = DigestUtils.sha1Hex(rawPassword).toUpperCase();
    String prefix = sha1.substring(0, 5);
    String suffix = sha1.substring(5);

    // GET https://api.pwnedpasswords.com/range/{prefix}
    String response = restClient.get()
        .uri("https://api.pwnedpasswords.com/range/{prefix}", prefix)
        .retrieve()
        .body(String.class);

    return response.lines()
        .anyMatch(line -> line.startsWith(suffix));
}
```

> **도입 시점**: Phase 2. 외부 API 의존성이므로 fallback(API 실패 시 통과) + circuit breaker 필요.
> API 가 비가용 시 비밀번호 변경을 차단하면 안 됨 — availability > security 트레이드오프.

#### 9.3.4 UserException 팩토리 메서드 추가

```java
// UserException — 비밀번호 변경 관련 추가 팩토리
public static UserException samePassword() {
    return new UserException(ErrorType.BAD_REQUEST,
        "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
}

public static UserException recentlyUsedPassword() {
    return new UserException(ErrorType.BAD_REQUEST,
        "최근 사용한 비밀번호는 재사용할 수 없습니다.");
}

public static UserException compromisedPassword() {
    return new UserException(ErrorType.BAD_REQUEST,
        "유출된 비밀번호입니다. 다른 비밀번호를 사용해주세요.");
}
```

#### 9.3.5 비밀번호 규칙 권장 기준 — 참고 문헌 비교

| 기준 | NIST 800-63B | OWASP | KISA | 본 프로젝트 |
|------|-------------|-------|------|-----------|
| 최소 길이 | 8자 (SHALL) | 8자 | 8자 | **8자** |
| 최대 길이 | 최소 64자 허용 (SHALL) | 128자 | — | **64자** |
| 복잡도 강제 | 금지 (SHALL NOT) | 비권장 | 2종류 이상 | **비적용** (NIST 준수) |
| 주기적 변경 | 금지 (SHALL NOT) | 비권장 | 90일 | **비적용** (NIST 준수) |
| 유출 목록 대조 | 필수 (SHALL) | 권장 | — | **설계 완료** (Phase 2) |
| 이력 재사용 금지 | 명시 없음 | 권장 | 최근 2개 | **최근 3개** |
| 현재 비밀번호 확인 | — | 필수 | 필수 | **적용 ✅** |
| 동일 비밀번호 변경 금지 | — | 권장 | — | **설계 완료** |

> **NIST "SHALL NOT" 규칙을 준수한다**: 복잡도 강제, 주기적 변경 강제는 적용하지 않는다.
> 이는 사용자가 `P@ssw0rd1!` → `P@ssw0rd2!` 같은 예측 가능한 패턴을 만들게 하여 보안을 약화시킨다는
> 연구 결과(Carnavalet & Mannan, 2014)에 기반한다.
```

---

## 10. CSRF / XSS / 기타 공격 방어

### 10.1 CSRF (현재 ✅)
- `CookieCsrfTokenRepository.withHttpOnlyFalse()` (JS 가 읽을 수 있도록)
- `CsrfCookieFilter` 가 모든 응답에서 토큰 강제 발급
- 프론트 `lib/api.ts` 가 `X-XSRF-TOKEN` 헤더 자동 첨부
- **추가 권장**: `XorCsrfTokenRequestAttributeHandler` 사용 (BREACH 공격 완화)

### 10.2 XSS
- React 가 기본 escape 처리 → 대부분 안전
- `dangerouslySetInnerHTML` 사용 금지 (또는 DOMPurify 통과)
- CSP 헤더 추가 권장:
  ```
  Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';
  ```
- **HttpOnly cookie**: 세션 쿠키는 무조건 HttpOnly → 토큰 탈취 차단

### 10.3 Click-jacking (현재 ✅)
- `X-Frame-Options: DENY` (운영) — 이미 dev profile 분리됨

### 10.4 Open Redirect
- `Location` 헤더에 사용자 입력 직접 사용 금지
- 화이트리스트 또는 상대 경로만 허용

### 10.5 Rate limiting (TODO)
- `Bucket4j` + Spring Boot starter
- 인증 엔드포인트 우선: `/api/auth/login`, `/api/user` (회원가입), 비밀번호 재설정
- IP 기준 + 사용자 기준 dual

### 10.6 Cookie 속성 (운영 권장)

```yaml
server:
  servlet:
    session:
      cookie:
        secure: true       # HTTPS only
        http-only: true    # JS 접근 차단
        same-site: lax     # CSRF 1차 방어 (Strict 가 더 안전하지만 OAuth 콜백과 충돌)
        max-age: 30m
```

---

## 11. 단계별 도입 로드맵

### Phase 0 — 현재 상태 ✅ (이미 완료)
- Session-based auth
- Argon2 password
- CSRF (Cookie repository)
- 세션 고정 방어
- 타이밍 공격 방어
- AuthUser custom principal
- 401 entry point
- frameOptions dev profile

### Phase 1 — 즉시 권장 ✅ (2026-04-12 완료)
1. ✅ **Rate limiting** (Bucket4j) — `RateLimitFilter`, IP 기반 분당 20회, XFF 불신
2. ✅ **비밀번호 변경 흐름** — `PUT /api/user/{id}/password`, 현재 비밀번호 재확인
3. ✅ **Token version 컬럼** — 비밀번호 변경 시 모든 세션 무효화 + 현재 세션 갱신
4. ✅ **운영 cookie 속성** — `application-prod.yaml` (secure, same-site lax, 30m)
5. ✅ **HTTPS 강제** — prod 프로필 `requiresChannel().requiresSecure()`
6. ✅ **Audit log** — `AuthEventListener` (@EventListener 성공/실패/로그아웃)
7. ✅ **XorCsrfTokenRequestAttributeHandler** — BREACH 압축 공격 완화
8. ✅ **Content-Security-Policy** — `default-src 'self'; script-src 'self'`

### Phase 1.5 — Redis 세션 관리 + 보안 강화 ✅ (2026-04-13 완료)
1. ✅ **Spring Session + Redis** — 세션 외부화 (`spring-boot-starter-session-data-redis`)
2. ✅ **`RedisIndexedSessionRepository`** — principal 기반 세션 인덱스 (`repository-type=indexed`)
3. ✅ **`SpringSessionBackedSessionRegistry`** — 다중 인스턴스 세션 레지스트리 (`SessionConfig`)
4. ✅ **`maximumSessions(3)`** — `CompositeSessionAuthenticationStrategy` + `ConcurrentSessionFilter`
5. ✅ **Sliding Session (30분)** — `spring.session.timeout=30m` + `AbsoluteSessionTimeoutFilter` (24시간)
6. ✅ **비밀번호 변경 시 즉시 세션 삭제** — `findByPrincipalName()` → 현재 세션 제외 일괄 삭제
7. ✅ **프로필 수정/비밀번호 변경 분리** — `UserUpdateRequest` 에서 password 제거, 백도어 차단
8. ✅ **Post fetch join** — `LazyInitializationException` 해결 + N+1 쿼리 제거
9. ✅ **RateLimitFilter Caffeine 캐시** — 전체 clear → 개별 eviction, dead code 정리
10. ✅ **설정값 외부화** — `AppSessionProperties`, `RateLimitProperties` (`@ConfigurationProperties`)
11. ✅ **AuthUser Serializable** — Redis 직렬화 지원
12. ✅ **세션 이벤트 감사 로그** — `SessionCreated/Deleted/ExpiredEvent` (세션 ID 축약)

> 상세 설계: [§12. Redis 세션 관리 설계](#12-redis-세션-관리-설계)

### Phase 2 — SNS 기능 확장 시
1. ✅ **Role 도입** — `Role` enum (`USER`/`ADMIN`) + `@EnableMethodSecurity` + `/api/admin/**` hasRole (2026-04-13)
2. **이메일 인증 흐름** (단기 JWT 1회용)
3. **비밀번호 재설정 흐름** (단기 JWT 1회용)
4. **계정 정지 / 복원** (Admin)
5. **Remember-Me** (PersistentTokenRepository)

### Phase 3 — 다중 클라이언트 / 운영 고도화
1. **2FA** (TOTP) — optional
2. **OAuth2 소셜 로그인** — Google, GitHub
3. **Audit log → 외부 SIEM** — Splunk, ELK

### Phase 4 — 모바일 / 외부 API
1. **JWT 발급 엔드포인트** (모바일 전용 `/api/auth/token`)
2. **Refresh token 흐름** (DB 저장 + 회전)
3. **API key 발급/회전** (외부 자동화 클라이언트)
4. **OAuth2 client credentials** (서드파티 시스템)

---

## 12. Redis 세션 관리 설계

### 12.1 왜 중앙 집중식 세션 저장소인가

| 문제 | 인메모리 세션 | Redis 세션 |
|------|-------------|-----------|
| 다중 인스턴스 | sticky session 필수, 인스턴스 장애 시 세션 유실 | 어느 인스턴스든 세션 접근 가능 |
| zero-downtime 배포 | 배포 시 사용자 강제 로그아웃 | 세션 유지 |
| 사용자별 세션 조회 | 불가능 (인메모리 SessionRegistry 는 단일 JVM 한정) | `FindByIndexNameSessionRepository` 로 전체 인스턴스의 세션 일괄 조회 |
| 세션 일괄 무효화 | tokenVersion 폴링 방식 (지연 있음) | 즉시 삭제 가능 |
| 재기동 시 | 모든 세션 소실 | Redis 에 영속, 사용자 영향 없음 |

> **결론**: SNS 서비스의 가용성과 다중 디바이스 세션 관리를 위해 Redis 를 세션 저장소로 채택한다.

### 12.2 의존성

```kotlin
// build.gradle.kts
dependencies {
    // Spring Session + Redis (Spring Boot 4.x 스타터)
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    // ↑ 내부적으로 spring-session-data-redis + spring-boot-starter-data-redis (Lettuce) 포함
    //   버전은 Spring Boot BOM 이 관리 — 명시적 버전 지정 불필요
}
```

> **참고**: Spring Boot 4.x 에서 `spring-boot-starter-session-data-redis` 스타터가 도입되어 별도로 `spring-session-data-redis` + `spring-boot-starter-data-redis` 를 나눠 선언할 필요가 없다.

### 12.3 `@EnableRedisIndexedHttpSession` 권장 분석

Spring Session 은 두 가지 Redis 저장소를 제공한다:

| | `RedisSessionRepository` | `RedisIndexedSessionRepository` |
|---|---|---|
| 활성화 | `@EnableRedisHttpSession` (Boot 3.x+ 기본) | `@EnableRedisIndexedHttpSession` |
| 보조 인덱스 | ❌ 없음 | ✅ principal name 으로 세션 조회 가능 |
| 세션 이벤트 | ❌ 미지원 | ✅ `SessionCreatedEvent`, `SessionDeletedEvent`, `SessionExpiredEvent` |
| `FindByIndexNameSessionRepository` | ❌ 미구현 | ✅ 구현 — `SpringSessionBackedSessionRegistry` 필수 전제 |
| 동시 세션 제어 (`maximumSessions`) | ❌ 불가 | ✅ 가능 |
| Redis Cluster 주의 | — | keyspace notification 이 random node 1개만 구독됨 → 일부 인덱스 정리 누락 가능 |

#### 권장: `@EnableRedisIndexedHttpSession` 사용

**이유:**

1. **동시 세션 제어** — SNS 서비스에서 사용자당 최대 세션 수를 제한하려면 `SpringSessionBackedSessionRegistry` 가 필요하고, 이는 `FindByIndexNameSessionRepository` 를 요구한다.
2. **비밀번호 변경 시 전체 세션 즉시 무효화** — 현재 tokenVersion 폴링 방식(최대 5분 지연)을 principal name 으로 세션을 찾아 즉시 삭제하는 방식으로 업그레이드.
3. **관리자 강제 로그아웃** — Phase 2 Admin 기능에서 특정 사용자의 모든 세션을 일괄 무효화.
4. **세션 이벤트** — 감사 로그(`AuthEventListener`)에 세션 생성/만료/삭제 이벤트 추가 가능.

**활성화 방법 (둘 중 택 1):**

```yaml
# 방법 A: application.yaml (Boot auto-config 활용 — 권장)
spring:
  session:
    store-type: redis
    redis:
      repository-type: indexed
```

```java
// 방법 B: @Configuration 어노테이션 (Boot auto-config 비활성화 시)
@Configuration
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig { }
```

> **주의**: Spring Boot auto-config 와 `@Enable*` 어노테이션을 동시에 사용하면 충돌한다. **방법 A (프로퍼티)** 를 권장한다.

#### 알려진 이슈

| 이슈 | 영향 | 대응 |
|------|------|------|
| 고아 인덱스 (spring-session#3453) | `RedisIndexedSessionRepository` 의 인덱스 키에 TTL 이 없어 orphan 발생 가능 | 주기적 Redis SCAN + 정리 배치 또는 모니터링 |
| Redis Cluster keyspace notification | 단일 노드만 구독 → 일부 세션 만료 이벤트 누락 | Sentinel 또는 단일 마스터 구성 권장 (이 프로젝트 규모에 적합) |
| namespace 프로퍼티 회귀 (spring-boot#3423) | Boot 3.5.0 에서 `spring.session.redis.namespace` 미적용 | Boot 4.0.5 에서 수정됨 |

### 12.4 `SpringSessionBackedSessionRegistry` — 동시 세션 제어

인메모리 `SessionRegistryImpl` 은 단일 JVM 에서만 유효하다. 다중 인스턴스에서는 `SpringSessionBackedSessionRegistry` 가 Redis 의 인덱스를 활용하여 전체 인스턴스에 걸친 세션을 관리한다.

```java
@Configuration
public class SecurityConfig<S extends Session> {

    private final FindByIndexNameSessionRepository<S> sessionRepository;

    // 생성자 주입
    public SecurityConfig(FindByIndexNameSessionRepository<S> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Bean
    public SpringSessionBackedSessionRegistry<S> sessionRegistry() {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fix -> fix.changeSessionId())
                .maximumSessions(3)
                    .sessionRegistry(sessionRegistry())
            )
            // ... 기존 설정
            .build();
    }
}
```

#### 사용자별 세션 일괄 무효화 (비밀번호 변경 시)

```java
// UserService — 비밀번호 변경 후 다른 디바이스 세션 즉시 삭제
public void changePassword(Long userId, String currentRaw, String newRaw, String currentSessionId) {
    User user = getById(userId);
    if (!passwordEncoder.matches(currentRaw, user.getPassword())) {
        throw UserException.invalidCredentials();
    }
    user.changePassword(passwordEncoder.encode(newRaw));
    user.bumpTokenVersion();

    // Redis 에서 해당 사용자의 모든 세션 조회 → 현재 세션 제외하고 삭제
    Map<String, ? extends Session> sessions =
        sessionRepository.findByPrincipalName(user.getEmail());
    sessions.forEach((id, session) -> {
        if (!id.equals(currentSessionId)) {
            sessionRepository.deleteById(id);
        }
    });
}
```

> **개선점**: 기존 tokenVersion 폴링 방식(최대 5분 지연) → Redis 즉시 삭제(0초 지연). tokenVersion 은 fallback 안전장치로 유지.

### 12.4.1 TokenVersionFilter — 매 요청 검증이 필요한 이유

#### 문제: 현재 방어 체계의 빈틈

비밀번호 변경 시 다른 세션을 무효화하는 현재 구현은 **2중 방어**로 구성된다:

```
방어 1차: invalidateOtherSessions()  — Redis 에서 세션 즉시 삭제
방어 2차: tokenVersion 비교          — /api/auth/me 에서만 검증
```

이 구조에는 **다음 시나리오에서 공격자의 세션이 살아남는 빈틈**이 있다:

#### 시나리오 1: 공격자가 `/api/auth/me`를 호출하지 않는 경우

```
[피해자: 디바이스 A]              [공격자: 탈취 세션 B]
   │                                │
   │ PUT /api/user/1/password       │
   │ → tokenVersion: 0 → 1         │
   │ → invalidateOtherSessions()   │
   │   Redis 에서 세션 B 삭제 시도  │
   │                                │
   │                                │ POST /api/post
   │                                │ ← 세션 B 가 Redis 에서 삭제되었으면 401
   │                                │ ← 그러나 아래 시나리오에서 삭제가 실패하면?
```

1차 방어(Redis 삭제)가 **성공하면** 문제없다. 그러나 **실패하는 경우**:

#### 시나리오 2: Redis 세션 인덱스 불일치 (spring-session#3453)

`findByPrincipalName(email)` 은 Redis 의 보조 인덱스(principal name index)를 조회한다. 이 인덱스에는 **알려진 이슈**(spring-session#3453)가 있다:

- 인덱스 키에 TTL 이 없어 **고아 인덱스** 가 발생할 수 있음
- 반대로, 세션은 존재하지만 **인덱스에서 누락**될 수도 있음
- Redis Cluster 환경에서는 keyspace notification 이 단일 노드만 구독하여 인덱스 정리가 불완전

인덱스 누락 시:
```
findByPrincipalName("victim@test.com")
→ 인덱스에 세션 B 가 없음 (누락)
→ 세션 B 는 삭제되지 않음
→ 공격자의 세션 B 는 여전히 유효
→ 2차 방어(tokenVersion)는 /api/auth/me 에서만 검증
→ 공격자가 /api/auth/me 를 호출하지 않으면 영원히 검증되지 않음
```

#### 시나리오 3: 요청 동시성 (in-flight 요청)

```
t=0    공격자: POST /api/post 요청 시작 (세션 B)
t=1    피해자: changePassword() → invalidateOtherSessions()
t=2    Redis 에서 세션 B 삭제됨
t=3    공격자: 요청이 이미 SecurityContext 에 로드된 상태 → 정상 처리됨
```

이 경우 단일 요청은 통과하지만, 이후 요청은 차단된다. 이것은 수용 가능한 수준이다.

#### 시나리오 4: 계정 삭제/정지 시

현재 `DELETE /api/user/{id}` 는 Redis 세션을 무효화하지 않는다 (#126). tokenVersion 검증이 매 요청에서 이루어진다면, 삭제된 사용자의 DB 조회 실패 → 세션 무효화가 자동으로 처리된다.

#### 결론: 왜 TokenVersionFilter 가 필요한가

| 방어 계층 | 역할 | 한계 |
|----------|------|------|
| **1차: Redis 세션 삭제** | 즉시 무효화 (0초) | 인덱스 누락 시 삭제 실패 가능 |
| **2차: tokenVersion 비교 (현재: /api/auth/me 만)** | fallback 안전장치 | 공격자가 해당 경로를 호출하지 않으면 검증 안 됨 |
| **2차 강화: TokenVersionFilter (전 경로)** | **모든 인증 요청에서 검증** | DB 조회 비용 → Caffeine 캐시로 완화 |

> **1차 방어(Redis 삭제)만으로 충분하지 않은가?**
> 대부분의 경우 충분하다. 그러나 **spring-session 의 알려진 인덱스 이슈(#3453)** 가 존재하는 한,
> Redis 삭제에만 의존하는 것은 "best-effort" 에 불과하다.
> 보안에서 "대부분 작동함"은 "취약함"과 같다. TokenVersionFilter 는 이 빈틈을 메운다.

#### 설계: TokenVersionFilter

```java
/**
 * 매 인증 요청에서 세션의 tokenVersion 과 DB 의 tokenVersion 을 비교.
 * 불일치 시 세션을 무효화한다.
 *
 * DB 부하를 줄이기 위해 userId → tokenVersion 을 Caffeine 캐시(30초 TTL)로 관리.
 * 비밀번호 변경 시 캐시를 evict 하면 최대 30초 이내 모든 세션이 무효화된다.
 */
public class TokenVersionFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final Cache<Long, Integer> tokenVersionCache;  // Caffeine, 30초 TTL

    @Override
    protected void doFilterInternal(...) {
        AuthUser authUser = getAuthUserFromSecurityContext();
        if (authUser == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int currentVersion = tokenVersionCache.get(
            authUser.getId(),
            id -> userRepository.findById(id)
                .map(User::getTokenVersion)
                .orElse(-1)  // 삭제된 사용자 → 불일치 유도
        );

        if (authUser.getTokenVersion() != currentVersion) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            FilterResponseUtils.writeJsonError(response, 401, "세션이 만료되었습니다.");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

#### 성능 영향 분석

| 항목 | TokenVersionFilter 없음 (현재) | TokenVersionFilter 도입 후 |
|------|-------------------------------|--------------------------|
| 매 요청 DB 조회 | 없음 | **없음** (Caffeine 캐시 hit) |
| 캐시 miss 시 | — | `userRepository.findById()` 1회 (30초당 1회) |
| 비밀번호 변경 후 무효화 지연 | 최대 24시간 (absolute timeout) | **최대 30초** (캐시 TTL) |
| 메모리 사용 | — | 활성 사용자 수 × ~50bytes (userId + int) |

> **30초 캐시 TTL 근거**: 비밀번호 변경은 긴급 상황(계정 탈취 의심)에서 발생한다.
> 30초는 보안과 성능의 균형점이다. 즉시성이 필요하면 TTL 을 5초로 줄이거나,
> 비밀번호 변경 시 `tokenVersionCache.invalidate(userId)` 를 호출하여 0초 무효화도 가능하다.

#### 방어 체계 최종 구성

```
[인증 요청 도달]
  │
  ├─ Spring Security: 세션에서 SecurityContext 복원 → AuthUser 주입
  │
  ├─ TokenVersionFilter (신규)
  │   ├─ Caffeine 캐시에서 userId → tokenVersion 조회
  │   ├─ 캐시 miss → DB 조회 → 캐시에 저장 (30초 TTL)
  │   ├─ authUser.tokenVersion ≠ currentVersion → 세션 무효화 + 401
  │   └─ 일치 → 통과
  │
  ├─ AbsoluteSessionTimeoutFilter: 24시간 절대 만료
  │
  └─ Controller 처리
```

```
[비밀번호 변경 시]
  │
  ├─ 1차 방어: invalidateOtherSessions() — Redis 세션 즉시 삭제 (0초)
  ├─ 2차 방어: User.tokenVersion 증가
  │            → TokenVersionFilter 가 캐시 TTL 이내(30초) 에 감지
  │            → 또는 changePassword 에서 tokenVersionCache.invalidate(userId) 호출 시 즉시 감지
  └─ 현재 세션: refreshSecurityContext() 로 tokenVersion 갱신 → 본인은 로그아웃 안 됨
```

### 12.5 SNS 서비스에 적합한 `sessionManagement` 설계

#### 동시 세션 수 (`maximumSessions`)

| 정책 | 값 | 근거 |
|------|---|------|
| **권장: `maximumSessions(3)`** | 3 | 스마트폰 + PC + 태블릿. SNS 특성상 다중 디바이스 동시 사용이 일반적 |
| 대안 A: 무제한 | — | Twitter/Threads 스타일. 관리 편의성 ↓ |
| 대안 B: 1 | — | 금융권 수준. SNS 에는 과도한 제한 → UX 악화 |

#### 초과 세션 처리 전략

| 전략 | 설정 | 동작 | SNS 적합도 |
|------|------|------|-----------|
| **가장 오래된 세션 만료** (권장) | `maxSessionsPreventsLogin(false)` (기본값) | 새 로그인 성공, 가장 오래된 세션 자동 만료 | ✅ 사용자 경험 우선 |
| 신규 로그인 거부 | `maxSessionsPreventsLogin(true)` | 기존 세션이 maximumSessions 개 이상이면 로그인 거부 | ❌ SNS 에 부적합 — 사용자가 기존 세션을 직접 종료해야 함 |

> **권장**: `maxSessionsPreventsLogin(false)` — SNS 사용자가 새 디바이스에서 로그인할 때 가장 오래된 세션이 자동 만료된다. 사용자에게 "다른 기기에서 로그아웃되었습니다" 알림을 보여줄 수 있다.

#### 전체 `sessionManagement` DSL 권장 설정

```java
http.sessionManagement(session -> session
    // 세션 생성 정책: 인증이 필요할 때만 생성
    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)

    // 세션 고정 공격 방어: 로그인 시 세션 ID 변경 (기존 속성 유지)
    .sessionFixation(fix -> fix.changeSessionId())

    // 동시 세션 제어: 사용자당 최대 3개, 초과 시 가장 오래된 세션 만료
    .maximumSessions(3)
        .sessionRegistry(sessionRegistry())
        // maxSessionsPreventsLogin(false) 는 기본값이므로 명시하지 않아도 됨

    // 유효하지 않은 세션 ID 요청 시 리다이렉트 대신 401 반환
    .invalidSessionStrategy((request, response) -> {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"세션이 만료되었습니다.\"}");
    })
);
```

### 12.6 세션 만료 전략: Sliding Session

#### Sliding Session 이란

매 요청마다 세션의 만료 시간이 갱신되는 방식이다. 사용자가 활동 중이면 세션이 유지되고, 비활동 상태가 지속되면 만료된다.

```
[사용자 활동]          [비활동 30분]          [만료]
  ├─ 요청 → TTL 리셋   ├─ 요청 → TTL 리셋   ├─ 30분 경과 → 세션 삭제
  │    (30분 뒤 만료)   │    (30분 뒤 만료)   │
  t=0                  t=15m                 t=45m → 만료: t=75m
```

Spring Session 은 기본적으로 **Sliding Session** 으로 동작한다. 매 요청에서 `session.setLastAccessedTime()` 이 갱신되고, Redis TTL 이 재설정된다.

#### TTL 설정

```yaml
# application.yaml (공통)
spring:
  session:
    timeout: 30m           # idle timeout (sliding)
    store-type: redis
    redis:
      repository-type: indexed
      namespace: sns        # Redis 키 접두사: "sns:sessions:..."

# application-prod.yaml (운영)
spring:
  session:
    timeout: 30m

server:
  servlet:
    session:
      cookie:
        secure: true
        http-only: true
        same-site: lax
        name: SESSION        # Spring Session 기본 쿠키 이름
```

> **참고**: Redis 의 실제 TTL 은 `maxInactiveInterval + 5분` 으로 설정된다. 추가 5분은 세션 정리 처리를 위한 여유 시간이다.

#### Absolute Timeout (절대 만료) — 보조 전략

Sliding Session 만으로는 공격자가 탈취한 세션을 지속적으로 갱신할 수 있다. 절대 만료를 보조적으로 적용:

| 만료 종류 | 값 | 구현 |
|----------|---|------|
| **Idle timeout** (sliding) | 30분 | `spring.session.timeout=30m` (Spring Session 기본) |
| **Absolute timeout** | 24시간 | 세션 attribute `SESSION_CREATED_AT` 저장 → 필터에서 검증 |

```java
// AbsoluteSessionTimeoutFilter — OncePerRequestFilter
Instant createdAt = (Instant) session.getAttribute("SESSION_CREATED_AT");
if (createdAt != null && Instant.now().isAfter(createdAt.plus(Duration.ofHours(24)))) {
    session.invalidate();
    // 401 응답
}
```

> **SNS 권장**: idle 30분 + absolute 24시간. Remember-Me 사용 시 absolute 7일로 연장 가능.

### 12.7 Redis 인프라 설정

#### Redis 연결 설정

```yaml
# application.yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      # password: ${REDIS_PASSWORD}    # 운영 환경에서는 환경변수로 주입
      # ssl:
      #   enabled: true                # 운영 환경에서 TLS 활성화

# application-dev.yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

#### Redis keyspace notification 활성화

`RedisIndexedSessionRepository` 가 세션 만료 이벤트를 수신하려면 Redis 의 keyspace notification 이 활성화되어야 한다:

```
# redis.conf
notify-keyspace-events Egx
```

> Spring Session 이 자동으로 `CONFIG SET notify-keyspace-events` 를 시도하지만, Redis 가 `CONFIG` 명령을 비활성화한 경우(AWS ElastiCache 등) 수동 설정 필요.

#### Redis 키 구조 (`namespace: sns`)

```
sns:sessions:<sessionId>              # Hash — 세션 데이터
sns:sessions:expires:<sessionId>      # String — TTL 마커
sns:expirations:<expireTime>          # Set — 해당 시간에 만료될 세션 ID 목록
sns:sessions:index:org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME:<email>
                                      # Set — 사용자(email)의 활성 세션 ID 목록
```

### 12.8 전체 구성 흐름도

```
[Browser]                    [Spring Boot (N instances)]           [Redis]
   │                              │                                   │
   │ POST /api/auth/login         │                                   │
   │ ─────────────────────────► │                                   │
   │                              │ authenticate()                    │
   │                              │ → SessionRegistry.registerNewSession()
   │                              │ ──────────────────────────────► │
   │                              │   HSET sns:sessions:<id>          │
   │                              │   SADD principal_index:<email>    │
   │ ◄── SESSION cookie           │                                   │
   │                              │                                   │
   │ GET /api/post (인증 요청)    │                                   │
   │ ─────────────────────────► │                                   │
   │                              │ HGETALL sns:sessions:<id> ──────► │
   │                              │ ◄── SecurityContext 복원           │
   │                              │ → lastAccessedTime 갱신 (sliding) │
   │                              │ ──────────────────────────────► │
   │                              │   EXPIRE 재설정                    │
   │ ◄── 200 응답                 │                                   │
   │                              │                                   │
   │ [30분 비활동]                │                                   │
   │                              │   Redis TTL 만료 ───────────────► │
   │                              │   SessionExpiredEvent 발행         │
   │                              │   principal_index 에서 제거        │
```

### 12.9 마이그레이션 계획 (인메모리 → Redis)

| 단계 | 작업 | 영향 |
|------|------|------|
| 1 | `build.gradle.kts` 에 `spring-boot-starter-session-data-redis` 추가 | 없음 |
| 2 | `application.yaml` 에 Redis 연결 + `spring.session.redis.repository-type=indexed` 설정 | 없음 |
| 3 | `SessionConfig` 클래스 작성 — `SpringSessionBackedSessionRegistry` 빈 등록 | 없음 |
| 4 | `SecurityConfig` 에서 `sessionManagement` DSL 에 `maximumSessions(3)` + `sessionRegistry()` 추가 | 기존 세션 무효화 (1회성) |
| 5 | `UserService.changePassword()` 에서 `findByPrincipalName()` + 일괄 삭제로 전환 | tokenVersion 폴링 → 즉시 삭제로 개선 |
| 6 | `application-dev.yaml` 에 로컬 Redis 설정 (`localhost:6379`) | 로컬 Redis 인스턴스 필요 |
| 7 | tokenVersion 검증 로직은 fallback 안전장치로 유지 | — |

> **배포 시 주의**: Redis 전환 시 기존 인메모리 세션은 모두 소실된다. 배포 공지 후 전환하거나, 사용자가 적은 시간대에 진행.

---

## 13. 알려진 트레이드오프

| 결정 | 트레이드오프 |
|------|-------------|
| Session-Primary | 모바일 클라이언트 추가 시 별도 인증 채널 필요 → Phase 4 에서 JWT 추가로 해소 |
| Redis 세션 저장소 | 인프라 의존성 추가 (Redis 서버 필요). dev 환경에서도 로컬 Redis 실행 필요 → Docker Compose 또는 embedded Redis 로 완화 |
| `RedisIndexedSessionRepository` | 고아 인덱스 발생 가능 (spring-session#3453). 주기적 모니터링 필요 |
| `maximumSessions(3)` | 4대 이상 디바이스 사용자는 가장 오래된 세션 자동 만료 → UX 알림으로 보완 |
| CSRF Cookie repository (JS readable) | XSS 시 토큰 노출 가능 → CSP + 입력 sanitization 으로 보완 |
| 비밀번호 정책 단순 (길이만) | NIST 권장에 부합. 복잡도 강제는 사용자 패턴(post-it)을 유도해 오히려 위험 |
| Argon2 (memory-hard) | 인증 비용 ↑ (매 로그인 ~50ms) → rate limiting 과 함께 운영 |
| 단기 토큰에 JWT | 키 회전 부담 → secret manager 필수 |
| OAuth2 미도입 | 사용자가 가입 부담 ↑ → Phase 2 에서 도입 |

---

## 14. 적용 이력 — Phase 1 구현 + 보안 취약점 수정

### 13.1 Phase 1 구현 상세 (2026-04-12)

#### Rate limiting (`RateLimitFilter.java`)

- **목적**: 인증 엔드포인트에 대한 brute force / credential stuffing 차단
- **구현**: `OncePerRequestFilter` + Bucket4j `Bandwidth.builder()`. POST `/api/auth/login`, `/api/user` 대상
- **IP 식별**: `request.getRemoteAddr()` **만** 사용. `X-Forwarded-For` 는 공격자가 임의 값으로 스푸핑 가능하므로 신뢰하지 않음 (운영 환경에서 신뢰 가능한 reverse proxy 뒤에서는 프록시 설정과 연동 필요)
- **제한**: IP 당 분당 20회. 초과 시 HTTP 429 + JSON 메시지 반환
- **OOM 방어**: `ConcurrentHashMap` 이 10,000 엔트리 초과 시 자동 정리 (운영에서는 Caffeine 또는 Redis 교체 권장)

```java
// RateLimitFilter 핵심 흐름
String clientIp = request.getRemoteAddr(); // XFF 불신
Bucket bucket = ipBuckets.computeIfAbsent("ip:" + clientIp, k -> createBucket());
if (!bucket.tryConsume(1)) {
  response.setStatus(429);
  return;
}
```

#### 비밀번호 변경 + Token version (`UserService.changePassword`, `User.tokenVersion`)

- **목적**: (a) 비밀번호 변경 시 현재 비밀번호 재확인, (b) 변경 후 다른 디바이스 세션 자동 무효화
- **구현**:
  1. `User.tokenVersion: int` 컬럼 (기본값 0). 비밀번호 변경 시 `bumpTokenVersion()` 으로 1 증가
  2. `AuthUser.tokenVersion` 필드 — 로그인 시점의 version 을 세션에 보관
  3. `GET /api/auth/me` 에서 DB User.tokenVersion vs 세션 AuthUser.tokenVersion 비교
  4. 불일치 → `session.invalidate()` + `SecurityContextHolder.clearContext()` + 401
  5. 현재 브라우저(비밀번호를 변경한 본인)는 `refreshSecurityContext()` 로 즉시 AuthUser 갱신하여 로그아웃 방지

```
[디바이스 A: 비밀번호 변경]     [디바이스 B: 기존 세션]
  │ PUT /api/user/1/password      │
  │ → changePassword()            │
  │ → user.bumpTokenVersion()     │
  │   (version: 0 → 1)           │
  │ → refreshSecurityContext()    │
  │   (세션 AuthUser.version = 1)│
  │ ← 204 OK                     │
  │                               │ GET /api/auth/me
  │                               │ AuthUser.version=0 ≠ DB.version=1
  │                               │ → session.invalidate()
  │                               │ ← 401 (세션 만료)
```

- **한계**: 디바이스 B 의 세션은 `/api/auth/me` 호출 시점에만 무효화됨 (폴링 주기 = 프론트 `useCurrentUser` staleTime 5분). 모든 요청에서 검증하려면 `OncePerRequestFilter` 필요 (Phase 3 Spring Session + Redis 시 도입 권장)

#### 인증 Audit log (`AuthEventListener.java`)

- **목적**: 침해 사고 분석 기반 마련
- **구현**: `@EventListener` 로 Spring Security 이벤트 수신
  - `AuthenticationSuccessEvent` → `audit.auth` 로거 INFO: `LOGIN_SUCCESS user=xxx`
  - `AbstractAuthenticationFailureEvent` → WARN: `LOGIN_FAILURE user=xxx reason=BadCredentialsException`
  - `LogoutSuccessEvent` → INFO: `LOGOUT user=xxx`
- **향후**: ELK / Splunk 등 외부 SIEM 으로 수집. IP/UA 추가 기록 (현재는 사용자 식별자만)

#### 운영 보안 설정

| 항목 | 파일 | 설명 |
|------|------|------|
| Cookie 속성 | `application-prod.yaml` | `secure: true`, `http-only: true`, `same-site: lax`, `timeout: 30m` |
| HTTPS 강제 | `SecurityConfig.java` | prod 프로필에서만 `requiresChannel().requiresSecure()` |
| BREACH 완화 | `SecurityConfig.java` | `CsrfTokenRequestAttributeHandler` → `XorCsrfTokenRequestAttributeHandler` 교체. 토큰을 XOR 마스킹하여 HTTP 압축 기반 토큰 추출 공격 차단 |
| CSP 헤더 | `SecurityConfig.java` | `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:` |

### 13.2 보안 취약점 수정 이력 (2026-04-13 해커 관점 리뷰)

침투 테스터 관점에서 공격 시나리오 기반으로 리뷰한 결과, Critical 2건 + High 3건을 발견하여 즉시 수정:

#### IDOR (Insecure Direct Object Reference) — Critical

**발견**: `PUT /api/user/{id}` 와 `DELETE /api/user/{id}` 에 본인 검증이 없어, 인증된 아무 사용자가 타인의 프로필·비밀번호를 덮어쓰거나 계정을 삭제할 수 있었음.

**공격 시나리오** (수정 전):
```bash
# 공격자(user 2)가 피해자(user 1)의 비밀번호를 자기 것으로 변경
curl -X PUT http://localhost:8080/api/user/1 \
  -H "Content-Type: application/json" \
  -b "JSESSIONID=<공격자 세션>" \
  -d '{"nickname":"pwned","password":"attacker-pw-123"}'
# → 200 OK. 이후 공격자가 피해자 계정으로 로그인 가능
```

**수정**: `@AuthenticationPrincipal AuthUser` 주입 + `requireOwnership(authUser, id)` 유틸 메서드로 본인 리소스 검증. 불일치 시 `AccessDeniedException` 발생 (403).

```java
private static void requireOwnership(AuthUser authUser, Long targetId) {
  if (authUser == null || !authUser.getId().equals(targetId)) {
    throw new AccessDeniedException("본인의 리소스만 수정/삭제할 수 있습니다.");
  }
}
```

**교훈**: PostController 의 `isAuthor()` 패턴은 적용되어 있었지만, UserController 에는 누락. 모든 mutation 엔드포인트에 소유권 검증은 필수.

#### Rate limit XFF 스푸핑 — High

**발견**: `X-Forwarded-For` 헤더를 신뢰하여 공격자가 매 요청마다 다른 IP 를 조작 가능. rate limiting 사실상 무력.

**수정**: `request.getRemoteAddr()` 만 사용. XFF 는 운영 환경의 trusted proxy 뒤에서만 신뢰 가능하며, 직접 노출된 서버에서는 스푸핑 벡터.

#### Rate limit OOM — High

**발견**: `ConcurrentHashMap<String, Bucket>` 에 eviction 없이 무한 증가 가능. XFF 스푸핑으로 수백만 고유 키 생성 시 JVM OOM.

**수정**: 10,000 엔트리 초과 시 맵 자동 정리. 운영에서는 Caffeine (`maximumSize` + `expireAfterWrite`) 또는 Redis 기반으로 교체 권장.

#### changePassword 세션 stale — High

**발견**: 비밀번호 변경 후 현재 세션의 `AuthUser.tokenVersion` 이 구버전으로 유지되어, 다음 `/api/auth/me` 호출 시 본인이 로그아웃됨.

**수정**: `UserController.changePassword()` 에서 `refreshSecurityContext()` 를 호출하여 현재 세션의 `AuthUser` 를 새 `tokenVersion` 으로 즉시 교체.

### 13.3 인지하되 수용한 위험 (Medium / Low)

| 위험 | 수용 근거 | 향후 대응 |
|------|----------|----------|
| 회원가입 이메일 존재 여부 노출 (409) | 일반적 UX 트레이드오프. Rate limiting 으로 대량 조회 억제 | 일반 응답으로 통일 (Phase 2) |
| dev 프로필 cookie 에 secure 미설정 | localhost HTTP 환경 필수 | 기본값을 restrictive 로 설정하고 dev 에서만 완화 (향후) |
| `PUT /api/user/{id}` 프로필 수정 | ~~password 필드 제거 완료 (04-13)~~ — nickname 만 변경. 비밀번호는 전용 `/password` 엔드포인트만 허용 | **해결됨** |
| CSP `style-src 'unsafe-inline'` | Tailwind / shadcn/ui 호환 필요 | nonce 기반 전환 (Phase 2) |
| 자동 계정 잠금 없음 | Rate limiting 으로 1차 방어 | N회 실패 → 임시 잠금 (Phase 2) |
| tokenVersion int overflow | 20억 회 비밀번호 변경 = 사실상 불가능 | long 전환 (필요 시) |

### 14.2 Phase 1.5 구현 상세 (2026-04-13)

#### Redis 세션 관리 인프라

- **의존성**: `spring-boot-starter-session-data-redis` + `com.github.ben-manes.caffeine:caffeine`
- **활성화**: `spring.session.redis.repository-type=indexed` (Boot auto-config, `@Enable*` 어노테이션 미사용)
- **`SessionConfig`**: `@ConditionalOnBean(FindByIndexNameSessionRepository.class)` — Redis 없는 테스트 환경에서 자동 비활성화
- **`SpringSessionBackedSessionRegistry`**: `FindByIndexNameSessionRepository` → Spring Security 동시 세션 제어 연결
- **`CompositeSessionAuthenticationStrategy`**: 커스텀 `RestAuthenticationFilter` 용 — (1) `ConcurrentSessionControlAuthenticationStrategy` → (2) `ChangeSessionIdAuthenticationStrategy` → (3) `RegisterSessionAuthenticationStrategy`
- **`AbsoluteSessionTimeoutFilter`**: `session.getCreationTime()` fallback으로 배포 전 기존 세션도 처리. `Duration` 은 `AppSessionProperties` 에서 주입

#### 비밀번호 변경 시 다른 세션 즉시 삭제

```
[비밀번호 변경 흐름 — 04-13 최종]
UserController.changePassword()
  → UserService.changePassword()
    → validateRawPassword(currentRawPassword)  ← #89 추가
    → validateRawPassword(newRawPassword)
    → passwordEncoder.matches(current, stored)
    → user.changePassword(encode(new))         ← tokenVersion 자동 증가
  → refreshSecurityContext(updatedUser)         ← 현재 세션 AuthUser 갱신
  → invalidateOtherSessions(email, sessionId)  ← Redis findByPrincipalName → 일괄 삭제
```

기존 tokenVersion 폴링 방식(최대 5분 지연) → Redis 즉시 삭제(0초). tokenVersion 은 fallback 안전장치로 유지.

#### 프로필 수정 / 비밀번호 변경 분리 (Critical #94 수정)

- **수정 전**: `PUT /api/user/{id}` 가 `UserUpdateRequest(nickname, password)` 를 받아 현재 비밀번호 확인 없이 교체 가능 → 세션 탈취 시 계정 탈취
- **수정 후**: `UserUpdateRequest(nickname)` 만 허용. `UserUpdateCommand` 삭제. `User.update()` → `updateNickname()` + `changePassword()` 분리. 비밀번호 변경은 전용 `PUT /api/user/{id}/password` 만 허용 (현재 비밀번호 재확인 + tokenVersion 자동 증가)

#### Post fetch join (Critical #81 수정)

- **수정 전**: `PostService.create()` 가 `getReferenceById()` → lazy proxy. Controller 에서 `PostResponse.from(post.getAuthor())` 시 `LazyInitializationException`
- **수정 후**: `findById()` 로 author 즉시 로드. `PostRepository` 에 `findWithAuthorById()` + `findAllWithAuthor()` fetch join 쿼리 추가. N+1 쿼리 문제도 동시 해결

#### RateLimitFilter Caffeine 캐시 교체 (Critical #82, Warning #90 수정)

- **수정 전**: `ConcurrentHashMap` + `emailBuckets` dead code + 10,000 초과 시 `ipBuckets.clear()` (전체 리셋 공격 가능)
- **수정 후**: dead code 제거. `Caffeine` 캐시 (`maximumSize` + `expireAfterAccess(10분)`) 로 개별 엔트리 자동 eviction. `RateLimitProperties` 로 설정값 외부화

#### 설정값 외부화 (Warning #86 #87 #88 수정)

| 설정 | 프로퍼티 키 | 기본값 | 적용 대상 |
|------|------------|--------|----------|
| 동시 세션 수 | `app.session.max-sessions-per-user` | 3 | `SecurityConfig`, `CompositeSessionAuthenticationStrategy` |
| 절대 세션 만료 | `app.session.absolute-timeout` | 24h | `AbsoluteSessionTimeoutFilter` |
| IP 분당 요청 수 | `app.rate-limit.ip-requests-per-minute` | 20 | `RateLimitFilter` |
| 최대 버킷 수 | `app.rate-limit.max-buckets` | 10000 | `RateLimitFilter` (Caffeine `maximumSize`) |

#### 기타 보안 개선 (Warning #83 #85 수정)

- `AuthController.me()`: `authUser == null` 방어적 체크 추가 — SecurityConfig 설정 변경 시 NPE 방지
- `RestAuthenticationFilter`: Content-Type 비교를 `equalsIgnoreCase` → `MediaType.parseMediaType().isCompatibleWith()` 로 교체 — `application/json; charset=utf-8` 등 변형 허용
- `AuthEventListener`: 세션 이벤트(`SessionCreated/Deleted/ExpiredEvent`) 감사 로그 추가, 세션 ID 앞 8자만 기록 (로그 유출 시 하이재킹 방지)

---

## 15. 결론

> **본 프로젝트는 단일 웹 클라이언트 + 단일 백엔드 구조이므로, Session-based 인증이 가장 단순하고 안전하며 운영 비용이 낮다.
> JWT 는 모바일/외부 통합/단기 1회용 토큰 등 명확한 목적이 있을 때 한정해서 도입한다.**
>
> **Phase 0 (기본 인증) + Phase 1 (보안 강화) + Phase 1.5 (Redis 세션 관리 + 보안 취약점 수정) 모두 완료.**
> 해커 관점 리뷰 2회에서 발견된 Critical 3건 + High 3건 + Warning 6건을 즉시 수정함.
> 다음 단계는 Phase 2 (Role 도입, 이메일 인증, 비밀번호 재설정).

---

## Appendix A — 빠른 의사결정 트리

```
인증이 필요한가?
├─ 아니오 → Public 엔드포인트
└─ 예 → 클라이언트는 무엇인가?
        ├─ 웹 브라우저 → Session (HttpOnly cookie + CSRF)
        ├─ 모바일 앱 → JWT (access + refresh)
        ├─ 서드파티 시스템 → OAuth2 client credentials 또는 API key
        └─ 1회용 링크 (이메일/비밀번호) → 단기 signed JWT (jti 1회 사용)
```

## Appendix B — 참고 자료

- OWASP Authentication Cheat Sheet
- OWASP Session Management Cheat Sheet
- NIST SP 800-63B (Digital Identity Guidelines)
- Spring Security Reference (6.x) — Session Management, CSRF, Method Security
- RFC 6750 (OAuth 2.0 Bearer Token), RFC 7519 (JWT), RFC 8725 (JWT BCP)
