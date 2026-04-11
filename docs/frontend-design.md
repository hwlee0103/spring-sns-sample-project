# Frontend Design — Next.js SNS UI

> 이 문서는 프론트엔드 아키텍처, 스택, 디렉토리 구조, 데이터 흐름, 컨벤션을 정의한다.
> 자세한 코드 규칙은 `.claude/skills/nextjs-frontend-rules/SKILL.md` 를 참조한다.

## 1. 개요

스레드/인스타그램/트위터 스타일 SNS UI. Next.js 16 App Router + React 19 + TypeScript.

| 항목 | 선택 | 비고 |
|------|------|------|
| Framework | **Next.js 16.2+ App Router** | Turbopack 기본, Pages Router 금지 |
| Language | **TypeScript strict + `noUncheckedIndexedAccess`** | `any` 금지 |
| Runtime | **React 19.2+** | Server Components 우선 |
| Styling | **Tailwind CSS v4** | `app/globals.css` 의 `@theme` 디렉티브 |
| UI Base | **shadcn/ui (base-nova)** | Tailwind 4 호환, `@base-ui/react` 기반 |
| Icons | **lucide-react** | |
| Server State | **TanStack Query v5** | 무한 스크롤, 옵티미스틱 업데이트 |
| Client State | **Zustand** | 모달/UI 토글에만 (현재 미사용) |
| Forms | **react-hook-form + zod** | `@hookform/resolvers` |
| HTTP | **fetch + lib/api.ts wrapper** | axios 사용 금지 |
| Theme | **next-themes** | 다크모드 |
| 빌드 | **Turbopack** | dev/build 모두 |

## 2. 디렉토리 구조

```
frontend/
├── app/                              # Next.js App Router
│   ├── layout.tsx                    # 루트 레이아웃 + Provider 주입
│   ├── page.tsx                      # 홈 = 피드
│   ├── login/page.tsx                # 로그인
│   ├── signup/page.tsx               # 가입
│   └── globals.css                   # Tailwind 4 + shadcn 디자인 토큰 (@theme)
├── components/
│   ├── ui/                           # shadcn 기본 컴포넌트 (button, input, card, dialog, ...)
│   ├── layout/
│   │   └── Header.tsx                # 전역 헤더 (로그인 상태 표시)
│   └── providers/
│       ├── QueryProvider.tsx         # TanStack Query Provider + Devtools
│       └── ThemeProvider.tsx         # next-themes
├── features/                         # ⭐ 도메인 코드는 여기 응집
│   ├── auth/
│   │   ├── api.ts                    # login / logout / fetchCurrentUser
│   │   ├── components/
│   │   │   ├── LoginForm.tsx
│   │   │   └── SignupForm.tsx
│   │   └── hooks/
│   │       ├── useCurrentUser.ts
│   │       ├── useLoginMutation.ts
│   │       └── useLogoutMutation.ts
│   ├── user/
│   │   ├── api.ts                    # fetchUsers, fetchUser, registerUser
│   │   └── types.ts                  # UserSchema + pageResponseSchema 헬퍼
│   └── post/
│       ├── api.ts                    # fetchFeed / createPost / updatePost / deletePost
│       ├── types.ts                  # PostSchema + POST_MAX_LENGTH
│       ├── components/
│       │   ├── FeedList.tsx          # 무한 스크롤 (Intersection Observer)
│       │   ├── FeedItem.tsx          # 단일 게시글 카드
│       │   ├── PostComposer.tsx      # 작성 폼
│       │   └── ComposerSection.tsx   # 로그인 상태에 따른 분기
│       └── hooks/
│           ├── useFeedQuery.ts       # useInfiniteQuery
│           └── useCreatePostMutation.ts
├── lib/
│   ├── api.ts                        # fetch wrapper + ApiError + zod 응답 검증
│   ├── env.ts                        # 환경변수 zod 검증 (실패 시 기동 차단)
│   ├── query-client.ts               # TanStack QueryClient (서버/클라 분리 싱글톤)
│   └── utils.ts                      # cn() 헬퍼
├── public/                           # 정적 자산
├── .env.local                        # NEXT_PUBLIC_API_BASE, BACKEND_URL
├── next.config.ts                    # /api/* → backend rewrites
├── tsconfig.json                     # strict + noUncheckedIndexedAccess + @/* alias
├── postcss.config.mjs                # @tailwindcss/postcss
├── components.json                   # shadcn 설정
└── package.json
```

**원칙**: **도메인 코드는 `features/{domain}/` 안에 응집**. `app/` 은 라우팅·레이아웃 셸만, 도메인 컴포넌트는 절대 직접 작성하지 않는다.

## 3. 레이어 책임

```
┌─────────────────────────────┐
│  app/ (Server Component)    │  라우팅, 메타데이터, 정적 셸
├─────────────────────────────┤
│  features/{d}/components/   │  도메인 UI (대부분 'use client')
├─────────────────────────────┤
│  features/{d}/hooks/        │  TanStack Query / Mutation 훅
├─────────────────────────────┤
│  features/{d}/api.ts        │  HTTP 호출 함수 (lib/api 사용)
├─────────────────────────────┤
│  features/{d}/types.ts      │  zod schema + 추론 타입
├─────────────────────────────┤
│  lib/api.ts                 │  fetch wrapper + 에러 변환 + zod 검증
└─────────────────────────────┘
```

## 4. 데이터 흐름

```
[Backend Spring]
       ↑↓ JSON
[next.config rewrites] ─── /api/:path* → http://localhost:8080/api/:path*
       ↑↓ same-origin (쿠키 자동)
[lib/api.ts]
  ├── fetch
  ├── credentials: 'include'
  ├── 응답 zod 검증
  └── ApiError(status, message, fieldErrors?)
       ↑↓
[features/{d}/api.ts]  도메인별 호출 함수 (스키마 명시)
       ↑↓
[features/{d}/hooks/]  useQuery / useInfiniteQuery / useMutation
       ↑↓
[features/{d}/components/]  React 컴포넌트
       ↑↓
[react-hook-form]  setError(field, message) ← ApiError.fieldErrors
```

### 핵심 설계: ApiError → 폼 에러 매핑
백엔드 `MethodArgumentNotValidException` 응답 (`{ errors: { email: "..." } }`) 이 `lib/api.ts` 에서 `ApiError.fieldErrors` 로 변환되고, 폼 컴포넌트에서 한 줄로 매핑된다:

```ts
catch (e) {
  if (e instanceof ApiError && e.fieldErrors) {
    for (const [field, message] of Object.entries(e.fieldErrors)) {
      setError(field as keyof Values, { message });
    }
  }
}
```

## 5. 백엔드 연동: rewrites 전략

`next.config.ts` 의 rewrites 로 `/api/*` 를 백엔드(`http://localhost:8080`) 로 프록시한다.

**효과**:
- 브라우저 입장에서 모든 요청이 same-origin → CORS 불필요
- 쿠키 세션이 자동 첨부됨 (`credentials: 'include'` 와 무관하게도 동작)
- 운영 환경에서는 NGINX/Cloud Run 리버스 프록시가 동일 역할

**환경변수**:
- `NEXT_PUBLIC_API_BASE` — 클라이언트 fetch 베이스 URL. 빈 문자열 = same-origin (기본).
- `BACKEND_URL` — 서버 측 rewrites destination. 빌드 시점 고정.

## 6. State 관리 분리 원칙

| 종류 | 도구 | 예시 |
|------|------|------|
| **서버 데이터** | TanStack Query | 피드, 사용자 정보, 게시글 |
| **폼 상태** | react-hook-form | 로그인 입력, 작성기 |
| **URL 상태** | Next.js searchParams | 검색어, 페이지 |
| **클라이언트 UI 상태** | useState (지역) / Zustand (전역) | 모달 open, 사이드바 토글 |
| **테마** | next-themes | 다크/라이트 |

**금지**: 서버 데이터를 Zustand 나 useState 로 복사·캐싱하지 말 것. TanStack Query 만 단일 소스로 사용한다.

## 7. Server vs Client Components

- **기본은 Server Component**. async function, 직접 fetch 가능.
- `'use client'` 는 다음 경우에만:
  - `useState`, `useEffect`, custom hook
  - 이벤트 핸들러 (`onClick`, `onChange`)
  - 브라우저 API
  - TanStack Query, Zustand, react-hook-form 사용
- 페이지(`page.tsx`) 는 Server Component 로 두고, 인터랙티브한 부분만 Client Component 로 분리하여 leaf 에 배치 (예: `app/page.tsx` Server → `FeedList`, `ComposerSection` Client)

## 8. Next.js 16 핵심 변경점 (반드시 숙지)

1. **Async Request APIs (Breaking)** — `params`, `searchParams`, `cookies()`, `headers()` 는 모두 `Promise`. 반드시 `await`.
2. **fetch 기본 캐싱 없음** — Server Component fetch 는 더 이상 자동 캐싱 X. `'use cache'` 또는 `next: { revalidate }` 명시.
3. **Turbopack 기본** — `--turbopack` 플래그 불필요.
4. **`middleware.ts` → `proxy.ts`** — 새 컨벤션.
5. **Tailwind 4** — `tailwind.config.ts` 없음. `app/globals.css` 의 `@theme` 디렉티브에서 토큰 정의.
6. **Node.js 20.9+, TypeScript 5.1+** 필수.

## 9. 페이지 / 라우팅

| 경로 | 컴포넌트 | 인증 | 설명 |
|------|----------|-----|------|
| `/` | `app/page.tsx` | 공개 | 홈 피드 |
| `/login` | `app/login/page.tsx` | 공개 | 로그인 |
| `/signup` | `app/signup/page.tsx` | 공개 | 회원가입 |

**향후 추가 예정**
| 경로 | 설명 |
|------|------|
| `/post/[id]` | 게시글 상세 + 댓글 |
| `/[username]` | 프로필 |
| `/notifications` | 알림 |
| `/search?q=...` | 검색 |

## 10. 디자인 시스템

- **shadcn/ui (base-nova 스타일)** 기반. `components/ui/` 에 설치된 컴포넌트만 사용.
- **디자인 토큰**: `app/globals.css` 의 `@theme` 블록에서 OKLCH 색공간으로 정의.
  - `--background`, `--foreground`, `--primary`, `--muted`, `--border`, `--destructive`, ...
  - 다크모드 토큰은 `.dark` 셀렉터에서 오버라이드.
- **Tailwind 클래스만 사용**, 인라인 스타일 / CSS Module / CSS-in-JS 금지.
- **반응형**: 모바일 우선. 기본 = 모바일, `md:` 이상으로 확장.
- **접근성**: 시맨틱 태그 (`<button>`, `<article>`, `<header>`), `aria-invalid`, `Label htmlFor`, 키보드 접근 가능.

## 11. 무한 스크롤 패턴

피드는 `useInfiniteQuery` + Intersection Observer:

```tsx
const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useFeedQuery();

useEffect(() => {
  const observer = new IntersectionObserver(([entry]) => {
    if (entry?.isIntersecting && !isFetchingNextPage) fetchNextPage();
  }, { rootMargin: "200px" });
  observer.observe(sentinelRef.current);
  return () => observer.disconnect();
}, [hasNextPage, isFetchingNextPage]);
```

페이지 종료 판정은 백엔드 `PageResponse.last` 플래그 사용.

## 12. 검증 동기화

폼 zod 스키마는 백엔드 Bean Validation 규칙과 1:1 일치시킨다:

| 필드 | 백엔드 | 프론트 zod |
|------|--------|-----------|
| email | `@Email @NotBlank` | `z.string().email()` |
| password | `@Size(min=8, max=64) @NotBlank` | `z.string().min(8).max(64)` |
| nickname | `@Size(min=2, max=20) @NotBlank` | `z.string().min(2).max(20)` |
| post.content | `@Size(max=500) @NotBlank` | `z.string().min(1).max(500)` |

서버 측 검증 실패는 `lib/api.ts` 가 `ApiError.fieldErrors` 로 표준화 → 폼이 `setError` 로 매핑.

## 13. 빌드 / 실행

```bash
cd frontend
npm install                     # 의존성 설치
npm run dev                     # 개발 서버 (localhost:3000)
npm run build                   # 프로덕션 빌드 (Turbopack)
npm start                       # 프로덕션 실행
npx tsc --noEmit                # 타입 체크
```

**전제**: 백엔드(`localhost:8080`) 가 동시에 떠 있어야 API 호출이 동작한다. dev 환경은 next rewrites 가 프록시 처리.

## 14. 알려진 제약 / TODO

- [ ] CSRF 토큰 미적용 (백엔드와 동일 사유)
- [ ] 게시글 상세/댓글 미구현
- [ ] 프로필/팔로우 미구현
- [ ] 알림 미구현
- [ ] 이미지 업로드 미구현 (백엔드 presigned URL 도입 필요)
- [ ] 옵티미스틱 업데이트 (좋아요/팔로우) 미적용
- [ ] E2E 테스트 (Playwright) 미구성
- [ ] Server Component 에서 prefetch + `HydrationBoundary` 패턴 미적용
- [ ] Error/Loading boundaries (`error.tsx`, `loading.tsx`) 미작성
