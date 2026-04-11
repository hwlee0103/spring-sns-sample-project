---
name: nextjs-frontend-rules
description: Define Next.js + React + TypeScript frontend conventions for the SNS social network app (Threads/Instagram/Twitter style). Use when user mentions frontend, Next.js, React, page, component, route, hook, UI, feed, post UI, login UI, layout, styling, form, mutation, query.
allowed-tools: Read, Write, Edit, Glob, Grep, Bash
---

# Next.js Frontend Development Rules

이 문서는 SNS(스레드/인스타그램/트위터 스타일) 프론트엔드를 구현할 때 따라야 할 표준 규칙이다.
백엔드(Spring Boot REST API)는 동일 리포의 루트에 위치하며, 프론트엔드는 `frontend/` 디렉토리에 모노레포 형태로 둔다.

> **중요 (Next.js 16)**: 이 프로젝트는 **Next.js 16+** 기준이다. Next.js 14 이하 지식과 다른 점이 많다. 코드를 작성하기 전에 `frontend/node_modules/next/dist/docs/` 또는 `frontend/AGENTS.md` 를 참고할 것.

## Tech Stack (고정)

| 분류 | 선택 | 비고 |
|------|------|------|
| Framework | **Next.js 16+ (App Router)** | Pages Router 사용 금지. Turbopack 기본 |
| Language | **TypeScript strict + `noUncheckedIndexedAccess`** | `any` 금지, `unknown` + narrowing |
| Runtime | **React 19+** | Server Components 우선 |
| Styling | **Tailwind CSS v4** | 설정은 `app/globals.css` 의 `@theme` 디렉티브 — `tailwind.config.ts` 없음 |
| UI Base | **shadcn/ui** (Tailwind 4 호환 버전) | `components/ui/` 에 설치 |
| Icons | **lucide-react** | |
| Server State | **TanStack Query v5** | API 호출 캐싱·재검증 |
| Client State | **Zustand** | 모달/드로어/UI 토글 등 가벼운 전역 상태에 한정 |
| Forms | **react-hook-form + zod** | 모든 사용자 입력 폼 |
| HTTP | **fetch + 얇은 wrapper (`lib/api.ts`)** | axios 사용 금지 |
| Auth | 쿠키 기반 세션 | 백엔드와 동일한 도메인/프록시, `credentials: 'include'` |

## Next.js 16 핵심 변경사항 (반드시 숙지)

1. **Async Request APIs (Breaking)** — `params`, `searchParams`, `cookies()`, `headers()`, `draftMode()` 는 모두 **`Promise`** 다. 동기 접근 불가.
   ```tsx
   // ❌ Next.js 14 방식
   export default function Page({ params }: { params: { id: string } }) {
     const id = params.id;
   }
   // ✅ Next.js 16 방식
   export default async function Page({ params }: { params: Promise<{ id: string }> }) {
     const { id } = await params;
   }
   ```
   타입 헬퍼는 `npx next typegen` 으로 `PageProps<'/blog/[slug]'>`, `LayoutProps`, `RouteContext` 자동 생성.
2. **fetch 기본 캐싱 없음** — Server Component 의 `fetch` 는 더 이상 자동 캐싱되지 않는다. 캐싱이 필요하면 `'use cache'` 디렉티브 또는 `next: { revalidate }` 옵션 명시.
3. **Turbopack 기본** — `next dev`, `next build` 모두 Turbopack 으로 동작. `--turbopack` 플래그 불필요.
4. **`middleware.ts` → `proxy.ts`** — 미들웨어 파일 컨벤션이 `proxy.ts` 로 변경됨 (deprecated).
5. **Tailwind 4** — `tailwind.config.ts` 가 없다. 디자인 토큰은 `app/globals.css` 의 `@theme` 블록에서 정의.
6. **Node.js 20.9+, TypeScript 5.1+** 필수.

## Project Structure

```
frontend/
├── app/                          # Next.js App Router 라우트
│   ├── (auth)/                   # 라우트 그룹: 로그인/회원가입 (레이아웃 분리용)
│   │   ├── login/page.tsx
│   │   └── signup/page.tsx
│   ├── (main)/                   # 라우트 그룹: 메인 앱 (사이드바 레이아웃)
│   │   ├── layout.tsx
│   │   ├── page.tsx              # 홈 피드
│   │   ├── post/[id]/page.tsx
│   │   └── @username/page.tsx    # 프로필
│   ├── api/                      # Next.js Route Handler (BFF 용도, 최소한만)
│   ├── layout.tsx                # 루트 레이아웃 (Provider 주입)
│   ├── globals.css               # Tailwind 진입점
│   └── not-found.tsx
├── components/
│   ├── ui/                       # shadcn/ui 기본 컴포넌트 (자동 생성)
│   └── layout/                   # 사이드바, 헤더 등 전역 레이아웃 컴포넌트
├── features/                     # 도메인 단위 폴더 (피처 슬라이스)
│   ├── feed/
│   │   ├── components/           # FeedList, FeedItem 등
│   │   ├── hooks/                # useFeedQuery 등
│   │   ├── api.ts                # feed 관련 API 호출 함수
│   │   └── types.ts              # 도메인 타입
│   ├── post/
│   ├── user/
│   ├── follow/
│   └── auth/
├── hooks/                        # 도메인 비종속 공용 훅 (useDebounce, useIntersectionObserver 등)
├── lib/
│   ├── api.ts                    # fetch wrapper (credentials, error handling)
│   ├── query-client.ts           # TanStack Query 설정
│   ├── utils.ts                  # cn(), formatDate 등
│   └── env.ts                    # 환경 변수 zod 검증
├── stores/                       # Zustand 전역 스토어 (UI 상태만)
├── types/                        # 전역 공용 타입 (백엔드 응답 매핑 등)
├── public/
├── .env.local                    # NEXT_PUBLIC_API_URL 등
├── next.config.ts
├── tsconfig.json
├── postcss.config.mjs            # Tailwind 4 plugin (config 파일 없음)
└── package.json
```

**원칙**: **도메인 코드는 `features/{domain}/` 안에 응집**시킨다. `components/`, `hooks/` 최상위는 도메인에 종속되지 않는 것만 둔다.

## TypeScript Rules

- `tsconfig.json` 에서 `"strict": true`, `"noUncheckedIndexedAccess": true` 활성화
- `any` 금지. 외부 데이터는 `unknown` 으로 받아 zod 로 narrow.
- 컴포넌트 props 는 항상 명시적 `interface` 또는 `type` 으로 선언
- 백엔드 응답 타입은 `features/{domain}/types.ts` 에 정의하고 `lib/api.ts` 에서 zod schema 로 런타임 검증
- `enum` 보다 `as const` + union 타입 선호
- import 경로: `@/` alias 사용 (`tsconfig` paths 설정)

```ts
// 권장
type PostStatus = 'draft' | 'published' | 'archived';
const POST_STATUS = ['draft', 'published', 'archived'] as const;

// 금지
enum PostStatus { ... }
```

## Component Rules

### Server vs Client Components

- **기본은 Server Component**. `'use client'` 는 다음 경우에만 선언:
  - `useState`, `useEffect`, `useReducer`, custom hook 사용
  - 이벤트 핸들러 (`onClick`, `onChange` 등)
  - 브라우저 전용 API (`localStorage`, `window`)
  - TanStack Query, Zustand 등 클라이언트 라이브러리 사용
- 데이터 페칭은 **가능한 한 Server Component 에서 직접 fetch** (캐싱 + RSC 페이로드 최소화)
- Client Component 는 **leaf 에 가깝게** 배치. 상위는 Server, 하위에서 Client island 로 분리.

### 컴포넌트 작성 규칙

- 한 파일 = 한 컴포넌트. 동일 폴더 내 보조 컴포넌트는 별 파일로 분리.
- 함수 컴포넌트 + named export. `export default` 는 Next.js 가 요구하는 경우(`page.tsx`, `layout.tsx`, `error.tsx`)만 사용.
- props 는 destructure. 5개 이상이면 별도 `interface` 로 추출.
- 컴포넌트 내부에 큰 inline JSX 가 있으면 sub-component 로 추출.
- 조건부 렌더링은 early return 또는 삼항. 4중 이상 중첩 금지.

```tsx
// features/feed/components/FeedItem.tsx
'use client';

import { Heart, MessageCircle } from 'lucide-react';
import type { Post } from '@/features/post/types';

interface FeedItemProps {
  post: Post;
  onLike: (postId: number) => void;
}

export function FeedItem({ post, onLike }: FeedItemProps) {
  return (
    <article className="border-b border-border p-4">
      {/* ... */}
    </article>
  );
}
```

## Naming Conventions

| 대상 | 규칙 | 예시 |
|------|------|------|
| 컴포넌트 파일 | PascalCase | `FeedItem.tsx`, `PostComposer.tsx` |
| 훅 파일 | camelCase, `use` prefix | `useFeedQuery.ts`, `useInfiniteScroll.ts` |
| 유틸 파일 | kebab-case 또는 camelCase | `format-date.ts`, `api.ts` |
| 라우트 파일 | Next.js 규약 그대로 | `page.tsx`, `layout.tsx`, `loading.tsx`, `error.tsx`, `not-found.tsx` |
| 타입 | PascalCase | `Post`, `UserProfile`, `FeedItemProps` |
| 상수 | UPPER_SNAKE_CASE | `MAX_POST_LENGTH`, `DEFAULT_PAGE_SIZE` |
| 함수 | camelCase, 동사로 시작 | `fetchFeed`, `formatRelativeTime` |

## API Integration

- 모든 백엔드 호출은 `lib/api.ts` 의 wrapper 를 거친다. fetch 직접 호출 금지.
- wrapper 는 다음을 처리:
  - `credentials: 'include'` 자동 설정 (쿠키 세션)
  - 베이스 URL: `process.env.NEXT_PUBLIC_API_URL` (zod 검증된 `lib/env.ts` 경유)
  - 에러: 응답 스키마 zod 검증 후 실패 시 `ApiError` 던짐
  - 백엔드 검증 오류(400 + `errors` 객체) 를 `FieldErrors` 로 변환
- 도메인별 호출 함수는 `features/{domain}/api.ts` 에 정의하고 wrapper 를 사용.
- 페이지네이션 응답은 백엔드 `PageResponse<T>` 와 동일한 형태로 받는다 (`content`, `page`, `totalPages` 등).

```ts
// features/feed/api.ts
import { api } from '@/lib/api';
import { PostSchema, type Post } from '@/features/post/types';
import { z } from 'zod';

const FeedPageSchema = z.object({
  content: z.array(PostSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
  first: z.boolean(),
  last: z.boolean(),
});

export async function fetchFeed(page = 0, size = 20) {
  return api.get('/api/feed', { query: { page, size }, schema: FeedPageSchema });
}
```

## Data Fetching & State

### 서버 데이터 — TanStack Query

- 모든 GET 호출은 TanStack Query 의 `useQuery` / `useInfiniteQuery` 를 사용.
- queryKey 는 도메인 + 파라미터로 구성: `['feed', { page, size }]`
- 변경(POST/PUT/DELETE) 후에는 `queryClient.invalidateQueries({ queryKey: [...] })` 로 캐시 무효화.
- 무한 스크롤은 `useInfiniteQuery` + Intersection Observer.
- Server Component 에서 prefetch 한 데이터는 `HydrationBoundary` 로 클라이언트 쿼리에 전달.

### 클라이언트 상태 — Zustand

- Zustand 는 **UI 상태에만** 사용 (모달 open/close, 사이드바 토글, composer 열림 여부 등).
- 서버에서 가져온 데이터는 절대 Zustand 에 저장하지 않는다 → TanStack Query 사용.
- 스토어 파일은 `stores/{name}-store.ts` 형태, 컴포지션을 위해 작은 단위로 분리.

## Forms & Validation

- 모든 사용자 입력 폼은 `react-hook-form` + `zod` 조합 사용.
- zod 스키마는 가능한 한 백엔드 검증 규칙(SKILL.md 의 `spring-api-rules`)과 동일하게 작성:
  - email: `z.string().email()`
  - password: `z.string().min(8).max(64)`
  - nickname: `z.string().min(2).max(20)`
- 서버 측 검증 오류(`errors: {field: message}`)는 `setError(field, ...)` 로 폼에 매핑.
- 제출 중에는 버튼 disabled + spinner.

```tsx
const schema = z.object({
  email: z.string().email('이메일 형식이 올바르지 않습니다.'),
  password: z.string().min(8, '비밀번호는 8자 이상').max(64),
});
type FormValues = z.infer<typeof schema>;
```

## Styling Rules

- **Tailwind CSS** 만 사용. 별도 CSS 파일·CSS-in-JS·인라인 `style` 객체 금지 (예외: 동적 계산 값).
- 클래스 결합은 `cn()` 헬퍼(`lib/utils.ts`) 사용. 조건부 클래스에 직접 문자열 결합 금지.
- 디자인 토큰(컬러, 간격)은 **Tailwind 4 의 `@theme` 디렉티브**(`app/globals.css`) 에서 관리. `tailwind.config.ts` 사용 금지.
- 다크모드: Tailwind `dark:` variant + `next-themes` 사용 (SNS 앱 필수).
- 반응형: 모바일 우선. 기본 = 모바일, `md:` 이상에서 확장.

## Routing (App Router)

- **라우트 그룹** `(auth)`, `(main)` 등으로 레이아웃 분리.
- 동적 세그먼트는 `[param]` (필수), `[[param]]` (선택), `[...slug]` (catch-all).
- `loading.tsx`, `error.tsx`, `not-found.tsx` 를 적극 활용해 UX 향상.
- Server Action 은 mutation 단순한 케이스에만 사용. 복잡한 mutation 은 API Route + TanStack Query.
- 메타데이터는 `export const metadata` 또는 `generateMetadata()` 로 SEO 최적화.

## SNS 도메인 가이드

SNS 앱의 핵심 화면별 권장 컴포넌트 구조:

| 화면 | 핵심 컴포넌트 | 데이터 |
|------|---------------|--------|
| 홈 피드 | `FeedList`, `FeedItem`, `PostComposer` | `useInfiniteQuery(['feed'])` |
| 게시글 상세 | `PostDetail`, `CommentList`, `CommentForm` | `useQuery(['post', id])` + `useInfiniteQuery(['comments', id])` |
| 프로필 | `ProfileHeader`, `ProfileTabs`, `UserPostList` | `useQuery(['user', username])` |
| 작성 | `PostComposer` (Modal) | `useMutation(createPost)` |
| 알림 | `NotificationList` | `useInfiniteQuery(['notifications'])` |
| 검색 | `SearchInput`, `SearchResults` | `useQuery(['search', q], { enabled: !!q })` + `useDebounce` |

- **Optimistic Update**: 좋아요/팔로우 같은 즉각 피드백 액션은 `onMutate` 로 옵티미스틱 처리.
- **이미지 업로드**: `next/image` 사용. 업로드는 백엔드 presigned URL 패턴 권장.
- **무한 스크롤**: `useInfiniteQuery` + Intersection Observer (`useInView`) 조합.
- **시간 표시**: `formatRelativeTime` 유틸로 "2분 전" 형태 — 서버/클라이언트 hydration 불일치 주의 (`suppressHydrationWarning` 또는 클라이언트 전용).

## Accessibility (필수)

- 모든 인터랙션 요소는 키보드 접근 가능. `<div onClick>` 금지 → `<button>` 사용.
- 이미지에 `alt` 필수. 장식 이미지는 `alt=""`.
- 폼 라벨: `<label htmlFor>` 또는 `aria-label`.
- 색상 대비: WCAG AA 이상. shadcn/ui 토큰 기본값 준수.
- 모달/드로어: focus trap + Escape 닫기 (shadcn `Dialog` 사용 시 자동).
- 스크린 리더 전용 텍스트는 `sr-only` 클래스 사용.

## Performance

- 이미지: 항상 `next/image`. `priority` 는 LCP 후보에만.
- 동적 import: 무거운 컴포넌트(에디터, 차트)는 `next/dynamic` + `ssr: false`.
- `React.memo` 는 측정 후 적용. 기본은 사용하지 않는다.
- TanStack Query `staleTime`/`gcTime` 을 도메인별로 설정 — SNS 피드는 짧게(1~2분), 프로필은 길게.
- 폰트: `next/font` 사용 (CLS 방지).

## Testing (도입 시)

- 컴포넌트: Vitest + React Testing Library
- E2E: Playwright (로그인 → 글 작성 → 좋아요 → 삭제 시나리오 우선)
- API mock: MSW (handler 는 `mocks/` 디렉토리)

## 금지 사항 요약

- ❌ Pages Router (`pages/` 디렉토리)
- ❌ `any`, `// @ts-ignore`
- ❌ axios (fetch 사용)
- ❌ CSS Module / styled-components (Tailwind 사용)
- ❌ Redux / MobX (TanStack Query + Zustand 사용)
- ❌ `useEffect` 로 데이터 페칭 (TanStack Query 사용)
- ❌ 도메인 코드를 `app/` 안에 직접 작성 (`features/` 사용)
- ❌ `<div onClick>` (시맨틱 태그 사용)
- ❌ 서버 데이터를 Zustand 에 저장
- ❌ 백엔드 응답을 zod 검증 없이 `as Type` 캐스팅
