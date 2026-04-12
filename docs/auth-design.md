# Authentication / Authorization Design Recommendation

> 본 문서는 이 프로젝트(Spring Boot SNS + Next.js)의 사용자 인증·인가 설계 권장안이다.
> 결론은 **"Session-Primary, JWT-Auxiliary"** 하이브리드이며, 그 근거와 적용 범위를 정리한다.

---

## 1. 현재 상태 요약

| 항목 | 구현 | 적용 일자 |
|------|------|-----------|
| 메커니즘 | Spring Security 6 + `HttpSessionSecurityContextRepository` | 04-11 |
| 비밀번호 해싱 | Argon2id (`Argon2Password4jPasswordEncoder` — password4j 1.8.2) | 04-12 |
| Principal | 커스텀 `AuthUser`(id/email/tokenVersion) — `UserDetailsServiceImpl` 가 로드 | 04-11 |
| 세션 ID | `JSESSIONID` (서버 메모리 저장) | 04-11 |
| 세션 고정 방어 | `ChangeSessionIdAuthenticationStrategy` — 로그인 시 세션 ID 재발급 | 04-11 |
| CSRF | `CookieCsrfTokenRepository` + **`XorCsrfTokenRequestAttributeHandler`** (BREACH 완화) + `CsrfCookieFilter` | 04-12 |
| 로그인 | `POST /api/auth/login` 수동 authenticate + 세션 저장 | 04-11 |
| 비밀번호 변경 | `PUT /api/user/{id}/password` — 현재 비밀번호 재확인 + tokenVersion bump | 04-12 |
| token version | `User.tokenVersion` + `AuthUser.tokenVersion` → `/api/auth/me` 에서 비교, 불일치 시 세션 무효화 | 04-12 |
| 현재 사용자 | `GET /api/auth/me` (`@AuthenticationPrincipal AuthUser`) + tokenVersion 검증 | 04-11 |
| 로그아웃 | `POST /api/auth/logout` (Spring Security `logout()` DSL) | 04-11 |
| 인가 (IDOR 방어) | `requireOwnership(authUser, id)` — PUT/DELETE /api/user/{id} 본인 검증 | 04-13 |
| Rate limiting | `RateLimitFilter` (Bucket4j) — IP 기반 분당 20회, XFF 불신 | 04-12 |
| Audit log | `AuthEventListener` — 로그인 성공/실패/로그아웃 이벤트 감사 로깅 | 04-12 |
| 운영 cookie | `application-prod.yaml` — secure, http-only, same-site lax, timeout 30m | 04-12 |
| HTTPS 강제 | `SecurityConfig` — prod 프로필에서만 `requiresChannel().requiresSecure()` | 04-12 |
| CSP 헤더 | `Content-Security-Policy: default-src 'self'; script-src 'self'` | 04-12 |
| H2 콘솔 제한 | `SecurityConfig` — dev 프로필에서만 접근 허용 (`AuthorizationDecision`) | 04-13 |
| 프론트 ↔ 백 | Next.js rewrites 로 same-origin 프록시, 쿠키 + XSRF-TOKEN 자동 첨부 | 04-11 |
| 인증 예외 | `unauthorizedEntryPoint` 가 401 통일 + `AuthenticationException` 전체 catch | 04-11 |
| 타이밍 공격 방어 | `UserDetailsServiceImpl` 가 더미 hash `passwordEncoder.matches` 호출 | 04-11 |

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

| 항목 | 권장 |
|------|------|
| 해싱 알고리즘 | **Argon2id** (현재 ✅) |
| 평문 노출 | 절대 금지 (로그, 응답, DB 모두) |
| 비밀번호 정책 | min 8, max 64, 단순 길이만. 복잡도 강제는 비권장 (NIST 800-63B) |
| 비밀번호 변경 | 현재 비밀번호 재입력 필수 (TODO — 현재 미구현) |
| 비밀번호 초과 시도 | rate limiting + 일시적 잠금 (TODO) |
| Pwned Passwords 체크 | optional — `haveibeenpwned` API 또는 다운로드 list |
| 2FA / TOTP | optional — `Google Authenticator` 호환, 추후 |

### 신규 권장 사항 (현재 미적용)

```java
// UserService.update — 비밀번호 변경 시 현재 비밀번호 재확인
public User updatePassword(Long id, String currentRawPassword, String newRawPassword) {
  User user = getById(id);
  if (!passwordEncoder.matches(currentRawPassword, user.getPassword())) {
    throw UserException.invalidCredentials();
  }
  // ... (newRawPassword 검증, 인코딩, user.update)
  // 비밀번호 변경 후 token version bump → 다른 디바이스 세션 자동 무효화
  user.bumpTokenVersion();
}
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

### Phase 2 — SNS 기능 확장 시
1. **Role 도입** — User/Admin
2. **이메일 인증 흐름** (단기 JWT 1회용)
3. **비밀번호 재설정 흐름** (단기 JWT 1회용)
4. **계정 정지 / 복원** (Admin)
5. **Remember-Me** (PersistentTokenRepository)

### Phase 3 — 다중 인스턴스 / 다중 클라이언트
1. **Spring Session + Redis** — 세션 외부화, 일괄 invalidate
2. **2FA** (TOTP) — optional
3. **OAuth2 소셜 로그인** — Google, GitHub
4. **Audit log → 외부 SIEM** — Splunk, ELK

### Phase 4 — 모바일 / 외부 API
1. **JWT 발급 엔드포인트** (모바일 전용 `/api/auth/token`)
2. **Refresh token 흐름** (DB 저장 + 회전)
3. **API key 발급/회전** (외부 자동화 클라이언트)
4. **OAuth2 client credentials** (서드파티 시스템)

---

## 12. 알려진 트레이드오프

| 결정 | 트레이드오프 |
|------|-------------|
| Session-Primary | 모바일 클라이언트 추가 시 별도 인증 채널 필요 → Phase 4 에서 JWT 추가로 해소 |
| HttpSession in-memory | 다중 인스턴스 미지원 → Phase 3 에서 Redis 로 외부화 |
| CSRF Cookie repository (JS readable) | XSS 시 토큰 노출 가능 → CSP + 입력 sanitization 으로 보완 |
| 비밀번호 정책 단순 (길이만) | NIST 권장에 부합. 복잡도 강제는 사용자 패턴(post-it)을 유도해 오히려 위험 |
| Argon2 (memory-hard) | 인증 비용 ↑ (매 로그인 ~50ms) → rate limiting 과 함께 운영 |
| 단기 토큰에 JWT | 키 회전 부담 → secret manager 필수 |
| OAuth2 미도입 | 사용자가 가입 부담 ↑ → Phase 3 에서 도입 |

---

## 13. 적용 이력 — Phase 1 구현 + 보안 취약점 수정

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
| `PUT /api/user/{id}` 가 nickname + password 동시 수정 | 프로필 수정과 비밀번호 변경이 별도 엔드포인트(`/password`)로 이미 분리됨 | 기존 `/api/user/{id}` 에서 password 필드 제거 검토 |
| CSP `style-src 'unsafe-inline'` | Tailwind / shadcn/ui 호환 필요 | nonce 기반 전환 (Phase 2) |
| 자동 계정 잠금 없음 | Rate limiting 으로 1차 방어 | N회 실패 → 임시 잠금 (Phase 2) |
| tokenVersion int overflow | 20억 회 비밀번호 변경 = 사실상 불가능 | long 전환 (필요 시) |

---

## 14. 결론

> **본 프로젝트는 단일 웹 클라이언트 + 단일 백엔드 구조이므로, Session-based 인증이 가장 단순하고 안전하며 운영 비용이 낮다.
> JWT 는 모바일/외부 통합/단기 1회용 토큰 등 명확한 목적이 있을 때 한정해서 도입한다.**
>
> **Phase 0 (기본 인증) + Phase 1 (보안 강화) 가 모두 완료**되었으며, 해커 관점 리뷰에서 발견된 Critical/High 취약점도 즉시 수정됨.
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
