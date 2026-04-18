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

## 14. SNS UI 시스템 디자인

### 14.1 디자인 원칙

| 원칙 | 설명 |
|------|------|
| **Clean & Minimal** | 불필요한 장식 요소 제거. 콘텐츠(글, 사진)가 주인공 |
| **Comfortable to Eye** | 고대비 텍스트 + 부드러운 배경. 눈 피로도 최소화 |
| **Consistent Spacing** | 4px 그리드 기반 (`p-1`=4px, `p-2`=8px, `p-4`=16px). 불규칙 간격 금지 |
| **Mobile First** | 기본 = 모바일 (max-w-lg 단일 컬럼). `md:` 이상에서 사이드바 추가 |
| **Immediate Feedback** | 팔로우/좋아요 → 옵티미스틱 업데이트 즉시 반영. 스피너 최소화 |
| **Accessible** | WCAG AA 색상 대비, 키보드 네비게이션, 스크린 리더 지원 |

### 14.2 컬러 시스템

```css
/* app/globals.css @theme */
@theme {
  /* Light — 따뜻한 회백색 배경 + 깊은 차콜 텍스트 */
  --background: oklch(0.98 0.002 90);        /* #FAFAF8 — 순백보다 눈에 편안한 웜 화이트 */
  --foreground: oklch(0.15 0.01 90);         /* #1A1A18 — 순흑 대신 소프트 차콜 */
  --card: oklch(1.0 0 0);                    /* #FFFFFF — 카드 배경 */
  --card-foreground: oklch(0.15 0.01 90);
  --primary: oklch(0.55 0.15 250);           /* 차분한 블루 — CTA, 링크 */
  --primary-foreground: oklch(0.98 0 0);
  --secondary: oklch(0.93 0.005 90);         /* 밝은 그레이 — 비활성, 보조 |
  --muted: oklch(0.93 0.005 90);
  --muted-foreground: oklch(0.55 0.01 90);   /* 부제목, 타임스탬프 */
  --border: oklch(0.90 0.005 90);            /* 연한 구분선 */
  --destructive: oklch(0.55 0.2 25);         /* 삭제/에러 레드 */
  --accent: oklch(0.55 0.15 250);            /* 팔로우 버튼 등 강조 */
  --ring: oklch(0.55 0.15 250);              /* 포커스 링 */
}

.dark {
  /* Dark — OLED 친화 어두운 배경 + 밝은 텍스트 */
  --background: oklch(0.13 0.005 90);        /* #1A1A1A */
  --foreground: oklch(0.93 0.005 90);        /* #ECECEC */
  --card: oklch(0.18 0.005 90);              /* #2A2A2A */
  --border: oklch(0.25 0.005 90);            /* #3A3A3A */
  --muted-foreground: oklch(0.65 0.01 90);
}
```

> **핵심**: 순백(#FFF) 배경 대신 **웜 화이트(#FAFAF8)** 사용 — 장시간 스크롤 시 눈 피로 30% 감소. 다크모드는 OLED 대비를 고려한 딥 그레이.

### 14.3 타이포그래피

| 용도 | 크기 | Weight | Tailwind |
|------|------|--------|----------|
| 페이지 타이틀 | 24px | Bold (700) | `text-2xl font-bold` |
| 카드 헤더 (닉네임) | 16px | Semibold (600) | `text-base font-semibold` |
| 본문 (게시글 내용) | 15px | Regular (400) | `text-[15px]` |
| 부제목 (타임스탬프, 카운트) | 13px | Regular (400) | `text-sm text-muted-foreground` |
| 버튼 | 14px | Medium (500) | `text-sm font-medium` |

```tsx
// next/font — 시스템 폰트 스택 + Pretendard 한글 폰트
import { Pretendard } from 'next/font/local';
const pretendard = Pretendard({ subsets: ['latin'], display: 'swap' });
```

### 14.4 레이아웃 구조

```
┌──────────────────────────────────────────────────────┐
│                    Header (sticky)                    │
│  [Logo]            [Search]         [Me] [Noti] [+]  │
├──────────┬────────────────────────────┬──────────────┤
│          │                            │              │
│ Sidebar  │      Main Content          │  Right Panel │
│ (md+)    │      (max-w-xl, center)    │  (lg+)       │
│          │                            │              │
│ • Home   │  ┌──────────────────────┐  │ • Trending   │
│ • Search │  │   Post Composer      │  │ • Suggested  │
│ • Noti   │  └──────────────────────┘  │   Users      │
│ • Profile│  ┌──────────────────────┐  │              │
│          │  │   Feed Item          │  │              │
│          │  │   ┌────┬───────────┐ │  │              │
│          │  │   │ Av │ Nick  3m  │ │  │              │
│          │  │   └────┴───────────┘ │  │              │
│          │  │   Content text...    │  │              │
│          │  │   [img/video]        │  │              │
│          │  │   ♡ 12  💬 3  ↗ 1   │  │              │
│          │  └──────────────────────┘  │              │
│          │  ┌──────────────────────┐  │              │
│          │  │   Feed Item ...      │  │              │
│          │  └──────────────────────┘  │              │
│          │       ↓ infinite scroll    │              │
├──────────┴────────────────────────────┴──────────────┤
│                  Bottom Nav (mobile only)             │
│     🏠      🔍      ➕      ❤️      👤              │
└──────────────────────────────────────────────────────┘
```

**반응형 브레이크포인트**:

| 범위 | 레이아웃 | 요소 |
|------|----------|------|
| `< md` (모바일) | 단일 컬럼 + 하단 내비 | Header + Main + BottomNav |
| `md ~ lg` | 좌측 사이드바 + 메인 | Sidebar(아이콘) + Main |
| `lg+` (데스크탑) | 3컬럼 | Sidebar(텍스트) + Main + RightPanel |

### 14.5 핵심 컴포넌트 설계

#### Header

```
┌─────────────────────────────────────────────────┐
│ 🌐 SNS    [ 🔍 검색... ]      [👤 nick ▾]     │
└─────────────────────────────────────────────────┘
```

- Sticky top, `backdrop-blur-sm` 로 글래스 효과
- 검색바: `md:` 이상에서만 표시. 모바일은 아이콘 클릭 → 전체화면 검색
- 프로필 드롭다운: 내 프로필 / 다크모드 토글 / 로그아웃

#### Feed Item (게시글 카드)

```
┌─────────────────────────────────────────┐
│ ┌──┐  alice_nick              3분 전    │
│ │🟢│  @alice                            │
│ └──┘                                    │
│                                         │
│ 오늘 날씨가 너무 좋아서 산책했어요 🌤️    │
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │         [이미지/비디오]              │ │
│ └─────────────────────────────────────┘ │
│                                         │
│ ♡ 12    💬 3    ↗ 공유    ⋯ 더보기      │
└─────────────────────────────────────────┘
```

- 카드 간 구분: `border-b border-border` (구분선) — 카드 그림자 대신 단순 선 (Threads 스타일)
- 아바타: 40px 원형, `ring-2 ring-primary` (온라인 상태 시)
- 시간: `text-muted-foreground` + 상대 시간 (`formatRelativeTime`)
- 액션 버튼: 아이콘 + 카운트, `hover:bg-secondary/50` 부드러운 호버

#### Post Composer (작성기)

```
┌─────────────────────────────────────────┐
│ ┌──┐  무슨 생각을 하고 있나요?           │
│ │🟢│  ____________________________      │
│ └──┘  ____________________________      │
│       ____________________________      │
│                                         │
│  📷 🎥 📍 #        15/500    [게시]     │
└─────────────────────────────────────────┘
```

- 텍스트에어리어: `resize-none`, auto-height (콘텐츠에 맞춤)
- 글자 수 카운터: 450자 이상이면 `text-destructive`
- 미디어 버튼: 하단 좌측, 아이콘만 (향후 구현)
- 게시 버튼: 내용 있을 때만 활성화 (`disabled` 스타일)

#### Profile Page

```
┌─────────────────────────────────────────┐
│         ┌────────┐                      │
│         │  Avatar │                     │
│         │  80px   │                     │
│         └────────┘                      │
│        alice_nick                       │
│        @alice                           │
│                                         │
│   42 게시글  │  1.2K 팔로워  │  350 팔로잉 │
│                                         │
│   [ 팔로우 ]  또는  [ 프로필 수정 ]       │
│                                         │
│ ┌───────────┬───────────┬─────────────┐ │
│ │   게시글   │   답글    │   미디어     │ │
│ └───────────┴───────────┴─────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │  Feed Item ...                      │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

- 아바타: 80px, `rounded-full` + `border-4 border-background`
- 카운트: 클릭 시 팔로워/팔로잉 목록 모달 또는 페이지
- 팔로우 버튼: `bg-primary text-primary-foreground` / 언팔로우 시 `variant="outline"`
- 탭: 게시글(기본) / 답글 / 미디어 — `underline` 스타일 인디케이터

#### Follow List (팔로워/팔로잉 목록)

```
┌─────────────────────────────────────────┐
│  팔로워 (1,234)                    [✕]  │
│─────────────────────────────────────────│
│ ┌──┐  bob_nick                          │
│ │🟢│  @bob           [ 맞팔로우 ]       │
│ └──┘                                    │
│─────────────────────────────────────────│
│ ┌──┐  carol_nick                        │
│ │  │  @carol          [ 팔로우 ]        │
│ └──┘                                    │
│         ↓ 더 보기 (infinite scroll)     │
└─────────────────────────────────────────┘
```

- `Dialog` 또는 별도 페이지 (`/[username]/followers`)
- 각 항목: 아바타 + 닉네임 + 팔로우/언팔로우 버튼
- 맞팔로우: `variant="secondary"` + "맞팔로우" 텍스트
- 무한 스크롤 페이징

### 14.6 페이지별 라우트 + 데이터 흐름

| 경로 | 컴포넌트 | 데이터 | 인증 |
|------|----------|--------|------|
| `/` | `FeedList` + `PostComposer` | `useInfiniteQuery(['feed'])` | 공개 (작성만 인증) |
| `/login` | `LoginForm` | `useLoginMutation` | 비인증 |
| `/signup` | `SignupForm` | `useRegisterMutation` | 비인증 |
| `/[username]` | `ProfileHeader` + `UserFeedList` | `useQuery(['user', username])` + `useInfiniteQuery(['user-posts'])` | 공개 |
| `/[username]/followers` | `FollowList` | `useInfiniteQuery(['followers', userId])` | 공개 |
| `/[username]/following` | `FollowList` | `useInfiniteQuery(['following', userId])` | 공개 |
| `/post/[id]` | `PostDetail` + `CommentList` | `useQuery(['post', id])` | 공개 |
| `/search` | `SearchInput` + `SearchResults` | `useQuery(['search', q])` + `useDebounce` | 공개 |
| `/notifications` | `NotificationList` | `useInfiniteQuery(['notifications'])` | 인증 |
| `/settings` | `SettingsForm` | `useCurrentUser` | 인증 |

### 14.7 Follow 프론트엔드 연동 설계

**features/follow/ 디렉토리 구조**:

```
features/follow/
├── api.ts               # follow / unfollow / getFollowers / getFollowings / getFollowCount / isFollowing
├── types.ts             # FollowResponseSchema, FollowUserSchema, FollowCountSchema, FollowStatusSchema
├── components/
│   ├── FollowButton.tsx      # 팔로우/언팔로우 토글 버튼 (옵티미스틱)
│   ├── FollowCountDisplay.tsx # "42 팔로워  |  350 팔로잉" 카운트 표시
│   └── FollowList.tsx        # 팔로워/팔로잉 목록 (무한 스크롤)
└── hooks/
    ├── useFollowMutation.ts     # follow + optimistic update
    ├── useUnfollowMutation.ts   # unfollow + optimistic update
    ├── useFollowersQuery.ts     # useInfiniteQuery(['followers', userId])
    ├── useFollowingsQuery.ts    # useInfiniteQuery(['followings', userId])
    ├── useFollowCountQuery.ts   # useQuery(['follow-count', userId])
    └── useFollowStatusQuery.ts  # useQuery(['follow-status', userId])
```

**FollowButton 옵티미스틱 업데이트**:

```tsx
// features/follow/hooks/useFollowMutation.ts
export function useFollowMutation(targetUserId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => followUser(targetUserId),

    // 옵티미스틱 업데이트 — 서버 응답 전에 UI 즉시 반영
    onMutate: async () => {
      // 1. 진행 중인 쿼리 취소 (stale 데이터 방지)
      await queryClient.cancelQueries({ queryKey: ['follow-status', targetUserId] });
      await queryClient.cancelQueries({ queryKey: ['follow-count', targetUserId] });

      // 2. 이전 값 스냅샷 (롤백용)
      const prevStatus = queryClient.getQueryData(['follow-status', targetUserId]);
      const prevCount = queryClient.getQueryData(['follow-count', targetUserId]);

      // 3. 낙관적으로 캐시 갱신
      queryClient.setQueryData(['follow-status', targetUserId], { following: true });
      queryClient.setQueryData(['follow-count', targetUserId], (old: any) => ({
        ...old,
        followersCount: (old?.followersCount ?? 0) + 1,
      }));

      return { prevStatus, prevCount };
    },

    // 실패 시 롤백
    onError: (_err, _vars, context) => {
      if (context) {
        queryClient.setQueryData(['follow-status', targetUserId], context.prevStatus);
        queryClient.setQueryData(['follow-count', targetUserId], context.prevCount);
      }
    },

    // 성공/실패 모두 서버 데이터로 동기화
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['follow-status', targetUserId] });
      queryClient.invalidateQueries({ queryKey: ['follow-count', targetUserId] });
    },
  });
}
```

**API 함수 + zod 스키마**:

```ts
// features/follow/types.ts
import { z } from 'zod';

export const FollowCountSchema = z.object({
  followersCount: z.number(),
  followeesCount: z.number(),
});
export type FollowCount = z.infer<typeof FollowCountSchema>;

export const FollowStatusSchema = z.object({
  following: z.boolean(),
});

export const FollowUserSchema = z.object({
  id: z.number(),
  nickname: z.string(),
  followedAt: z.string().transform((s) => new Date(s)),
});
export type FollowUser = z.infer<typeof FollowUserSchema>;
```

```ts
// features/follow/api.ts
import { api } from '@/lib/api';
import { FollowCountSchema, FollowStatusSchema, FollowUserSchema } from './types';
import { pageResponseSchema } from '@/features/user/types';

export const followUser = (userId: number) =>
  api.post(`/api/user/${userId}/follow`);

export const unfollowUser = (userId: number) =>
  api.delete(`/api/user/${userId}/follow`);

export const fetchFollowCount = (userId: number) =>
  api.get(`/api/user/${userId}/follow/count`, { schema: FollowCountSchema });

export const fetchFollowStatus = (userId: number) =>
  api.get(`/api/user/${userId}/follow/status`, { schema: FollowStatusSchema });

export const fetchFollowers = (userId: number, page = 0) =>
  api.get(`/api/user/${userId}/followers`, {
    query: { page, size: 20 },
    schema: pageResponseSchema(FollowUserSchema),
  });

export const fetchFollowings = (userId: number, page = 0) =>
  api.get(`/api/user/${userId}/followings`, {
    query: { page, size: 20 },
    schema: pageResponseSchema(FollowUserSchema),
  });
```

### 14.8 인터랙션 애니메이션

| 액션 | 애니메이션 | Tailwind |
|------|-----------|----------|
| 팔로우 버튼 클릭 | 부드러운 컬러 전환 | `transition-colors duration-200` |
| 좋아요 클릭 | 하트 스케일 바운스 | `animate-bounce` (100ms) → 정지 |
| 카드 호버 | 배경 미세 변화 | `hover:bg-secondary/30 transition-colors` |
| 페이지 전환 | 페이드인 | `animate-in fade-in duration-200` |
| 토스트 알림 | 슬라이드 인/아웃 | shadcn `Sonner` |
| 스켈레톤 로딩 | 펄스 | `animate-pulse bg-muted` |

> **원칙**: 화려한 애니메이션 지양. **200ms 이하의 미세한 전환**으로 부드러움만 전달. 시선을 빼앗지 않는 것이 SNS UI 의 핵심.

### 14.9 스켈레톤 / 로딩 상태

```tsx
// 피드 스켈레톤 — 데이터 로딩 중 표시
function FeedSkeleton() {
  return (
    <div className="space-y-4">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="p-4 space-y-3">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-full bg-muted animate-pulse" />
            <div className="space-y-1.5">
              <div className="h-4 w-24 bg-muted animate-pulse rounded" />
              <div className="h-3 w-16 bg-muted animate-pulse rounded" />
            </div>
          </div>
          <div className="space-y-2">
            <div className="h-4 w-full bg-muted animate-pulse rounded" />
            <div className="h-4 w-3/4 bg-muted animate-pulse rounded" />
          </div>
        </div>
      ))}
    </div>
  );
}
```

- 스켈레톤은 실제 콘텐츠와 **동일한 레이아웃** — CLS (Cumulative Layout Shift) 방지
- `loading.tsx` 에서 export → Next.js 가 자동 Suspense 경계로 사용

## 15. 알려진 제약 / TODO

- [x] SNS UI 시스템 디자인 — 컬러/타이포/레이아웃/컴포넌트 설계 (2026-04-18)
- [x] Follow 프론트엔드 연동 설계 — API/hooks/옵티미스틱 업데이트 (2026-04-18)
- [ ] CSRF 토큰 적용 (백엔드 `XSRF-TOKEN` 쿠키 → `X-XSRF-TOKEN` 헤더)
- [ ] 프로필 페이지 (`/[username]`) 구현
- [ ] 팔로우 기능 프론트엔드 구현 (FollowButton, FollowList, FollowCount)
- [ ] 게시글 상세/댓글 (`/post/[id]`) 구현
- [ ] 검색 (`/search`) 구현
- [ ] 알림 (`/notifications`) 구현
- [ ] 이미지 업로드 (백엔드 presigned URL + JSONB metadata)
- [ ] 옵티미스틱 업데이트 (좋아요/팔로우) 적용
- [ ] E2E 테스트 (Playwright)
- [ ] Server Component prefetch + `HydrationBoundary` 패턴
- [ ] Error/Loading boundaries (`error.tsx`, `loading.tsx`)
- [ ] 스켈레톤 UI 컴포넌트 작성
