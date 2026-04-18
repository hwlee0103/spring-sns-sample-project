# Post Service Frontend Design — 게시글 프론트엔드 설계

> 이 문서는 게시글(Post) 도메인의 프론트엔드 구조, 컴포넌트, 훅, 상태 관리, UI 인터랙션을 정의한다.
> 백엔드 설계: `docs/backend/post-design.md` 참조.

## 1. 디렉토리 구조

```
features/post/
├── api.ts                        # HTTP 호출 함수 (lib/api.ts 경유)
├── types.ts                      # zod 스키마 + 추론 타입
├── constants.ts                  # POST_MAX_LENGTH, EDIT_WINDOW_MINUTES
├── components/
│   ├── FeedList.tsx              # 피드 무한 스크롤 (메인/프로필/답글)
│   ├── FeedItem.tsx              # 단일 게시글 카드 (유형별 렌더링)
│   ├── FeedItemSkeleton.tsx      # 로딩 스켈레톤
│   ├── PostComposer.tsx          # 작성기 (일반/답글/인용)
│   ├── PostDetail.tsx            # 게시글 상세 + 답글 스레드
│   ├── PostActions.tsx           # 액션 바 (답글/리포스트/좋아요/공유)
│   ├── QuoteCard.tsx             # 인용된 게시글 임베드 카드
│   ├── RepostBanner.tsx          # "alice님이 리포스트" 배너
│   ├── EditIndicator.tsx         # "(수정됨)" 표시 + 수정 가능 시간 카운트다운
│   ├── DeletedPostPlaceholder.tsx # "이 게시글은 삭제되었습니다" 플레이스홀더
│   └── PostMenu.tsx              # ⋯ 더보기 (수정/삭제/신고)
├── hooks/
│   ├── useFeedQuery.ts           # 메인 피드 useInfiniteQuery
│   ├── useUserPostsQuery.ts      # 사용자 게시글 useInfiniteQuery
│   ├── usePostQuery.ts           # 단건 useQuery
│   ├── useRepliesQuery.ts        # 답글 목록 useInfiniteQuery
│   ├── useQuotesQuery.ts         # 인용 목록 useInfiniteQuery
│   ├── useCreatePostMutation.ts  # 게시글 생성 (일반/답글/인용/리포스트)
│   ├── useUpdatePostMutation.ts  # 수정 (20분 윈도우)
│   ├── useDeletePostMutation.ts  # 삭제 (soft delete)
│   └── useRepostMutation.ts      # 리포스트 (옵티미스틱)
└── utils/
    ├── formatRelativeTime.ts     # "3분 전", "2시간 전" 등
    └── isEditable.ts             # 클라이언트 측 수정 가능 판정
```

## 2. 타입 + zod 스키마

```ts
// features/post/types.ts
import { z } from 'zod';

export const PostType = {
  ORIGINAL: 'ORIGINAL',
  REPLY: 'REPLY',
  QUOTE: 'QUOTE',
  REPOST: 'REPOST',
} as const;
export type PostType = (typeof PostType)[keyof typeof PostType];

export const UserSummarySchema = z.object({
  id: z.number(),
  nickname: z.string(),
});

export const PostSchema: z.ZodType<Post> = z.object({
  id: z.number(),
  content: z.string().nullable(),
  author: UserSummarySchema,
  type: z.enum(['ORIGINAL', 'REPLY', 'QUOTE', 'REPOST']),
  parentId: z.number().nullable(),
  quoteId: z.number().nullable(),
  repostId: z.number().nullable(),
  quotedPost: z.lazy(() => PostSchema).nullable().optional(),
  replyCount: z.number(),
  repostCount: z.number(),
  likeCount: z.number(),
  viewCount: z.number(),
  shareCount: z.number(),
  editable: z.boolean(),
  deleted: z.boolean(),
  createdAt: z.string().transform((s) => new Date(s)),
  updatedAt: z.string().transform((s) => new Date(s)),
});
export type Post = z.infer<typeof PostSchema>;

export const PostPageSchema = z.object({
  content: z.array(PostSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
  first: z.boolean(),
  last: z.boolean(),
});
```

```ts
// features/post/constants.ts
export const POST_MAX_LENGTH = 500;
export const EDIT_WINDOW_MINUTES = 20;
```

## 3. API 함수

```ts
// features/post/api.ts
import { api } from '@/lib/api';
import { PostSchema, PostPageSchema } from './types';

/** 게시글 생성 — 통합 (일반/답글/인용/리포스트) */
export const createPost = (body: {
  content?: string;
  parentId?: number;
  quoteId?: number;
  repostId?: number;
}) => api.post('/api/v1/post', { body, schema: PostSchema });

/** 게시글 수정 (20분 윈도우) */
export const updatePost = (id: number, content: string) =>
  api.put(`/api/v1/post/${id}`, { body: { content }, schema: PostSchema });

/** 게시글 삭제 (soft delete) */
export const deletePost = (id: number) =>
  api.delete(`/api/v1/post/${id}`);

/** 단건 조회 */
export const fetchPost = (id: number) =>
  api.get(`/api/v1/post/${id}`, { schema: PostSchema });

/** 메인 피드 (전체 최신순) */
export const fetchFeed = (page = 0, size = 20) =>
  api.get('/api/v1/post', { query: { page, size }, schema: PostPageSchema });

/** 답글 목록 (스레드) */
export const fetchReplies = (postId: number, page = 0) =>
  api.get(`/api/v1/post/${postId}/replies`, { query: { page, size: 20 }, schema: PostPageSchema });

/** 인용 목록 */
export const fetchQuotes = (postId: number, page = 0) =>
  api.get(`/api/v1/post/${postId}/quotes`, { query: { page, size: 20 }, schema: PostPageSchema });

/** 사용자의 게시글 */
export const fetchUserPosts = (userId: number, page = 0) =>
  api.get(`/api/v1/user/${userId}/posts`, { query: { page, size: 20 }, schema: PostPageSchema });
```

## 4. 훅 설계

### 4.1 Query 훅 — queryKey 전략

| 훅 | queryKey | staleTime | 비고 |
|----|----------|-----------|------|
| `useFeedQuery` | `['feed', { size }]` | 60초 | 메인 피드 — 짧은 stale (새 글 반영) |
| `usePostQuery` | `['post', id]` | 120초 | 단건 — 중간 |
| `useRepliesQuery` | `['replies', postId]` | 60초 | 답글 스레드 |
| `useQuotesQuery` | `['quotes', postId]` | 120초 | 인용 목록 |
| `useUserPostsQuery` | `['user-posts', userId]` | 120초 | 프로필 피드 |

```ts
// features/post/hooks/useFeedQuery.ts
export function useFeedQuery() {
  return useInfiniteQuery({
    queryKey: ['feed'],
    queryFn: ({ pageParam = 0 }) => fetchFeed(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.last ? undefined : lastPage.page + 1,
    staleTime: 60_000,
  });
}
```

### 4.2 Mutation 훅

```ts
// features/post/hooks/useCreatePostMutation.ts
export function useCreatePostMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createPost,
    onSuccess: (newPost) => {
      // 피드 캐시 무효화 → 새 글 반영
      queryClient.invalidateQueries({ queryKey: ['feed'] });

      // 답글인 경우 부모 게시글 캐시도 무효화 (replyCount 갱신)
      if (newPost.parentId) {
        queryClient.invalidateQueries({ queryKey: ['post', newPost.parentId] });
        queryClient.invalidateQueries({ queryKey: ['replies', newPost.parentId] });
      }

      // 리포스트인 경우 원본 게시글 캐시 무효화 (repostCount 갱신)
      if (newPost.repostId) {
        queryClient.invalidateQueries({ queryKey: ['post', newPost.repostId] });
      }
    },
  });
}
```

### 4.3 리포스트 옵티미스틱 업데이트

```ts
// features/post/hooks/useRepostMutation.ts
export function useRepostMutation(targetPostId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => createPost({ repostId: targetPostId }),

    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: ['post', targetPostId] });
      const prev = queryClient.getQueryData(['post', targetPostId]);

      // 낙관적: repostCount + 1
      queryClient.setQueryData(['post', targetPostId], (old: any) =>
        old ? { ...old, repostCount: old.repostCount + 1 } : old
      );
      return { prev };
    },

    onError: (_err, _vars, context) => {
      if (context?.prev) {
        queryClient.setQueryData(['post', targetPostId], context.prev);
      }
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['post', targetPostId] });
      queryClient.invalidateQueries({ queryKey: ['feed'] });
    },
  });
}
```

### 4.4 삭제 — 피드에서 즉시 제거

```ts
// features/post/hooks/useDeletePostMutation.ts
export function useDeletePostMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deletePost,
    onSuccess: (_data, postId) => {
      // 피드에서 즉시 제거 (서버 재조회 전)
      queryClient.setQueriesData(
        { queryKey: ['feed'] },
        (old: any) => {
          if (!old?.pages) return old;
          return {
            ...old,
            pages: old.pages.map((page: any) => ({
              ...page,
              content: page.content.filter((p: any) => p.id !== postId),
            })),
          };
        }
      );
      // 전체 무효화
      queryClient.invalidateQueries({ queryKey: ['feed'] });
    },
  });
}
```

## 5. 컴포넌트 설계

### 5.1 FeedItem — 유형별 렌더링

```
┌─────────────────────────────────────────────────┐
│ [REPOST 인 경우]                                 │
│ ↗ alice님이 리포스트                              │
│                                                   │
│ ┌──┐  bob_nick                     3분 전 (수정됨)│
│ │🟢│  @bob                                        │
│ └──┘                                              │
│                                                   │
│ 오늘 날씨가 너무 좋아서 산책했어요 🌤️              │
│                                                   │
│ [QUOTE 인 경우 — 인용 카드 임베드]                 │
│ ┌─────────────────────────────────────────────┐   │
│ │ ┌──┐ carol_nick         2시간 전             │   │
│ │ │  │ 여행 사진 공유합니다                     │   │
│ │ └──┘ [이미지 썸네일]                          │   │
│ └─────────────────────────────────────────────┘   │
│                                                   │
│ ♡ 12     💬 3     ↗ 2     🔁 5    ⋯              │
│ 좋아요   답글    인용    리포스트   더보기          │
└─────────────────────────────────────────────────┘
```

**렌더링 분기**:

| PostType | 렌더링 | 비고 |
|----------|--------|------|
| ORIGINAL | 기본 카드 | 표준 게시글 |
| REPLY | 기본 카드 + 상단에 "↩ @부모작성자에게 답글" 표시 | 스레드 뷰에서는 세로선 연결 |
| QUOTE | 기본 카드 + 하단에 `<QuoteCard>` 임베드 | 인용된 원본을 카드 내 카드로 표시 |
| REPOST | `<RepostBanner>` + 원본 FeedItem | 본인 content 없이 원본 그대로 표시 |
| deleted | `<DeletedPostPlaceholder>` | "이 게시글은 삭제되었습니다" |

```tsx
// features/post/components/FeedItem.tsx
'use client';

interface FeedItemProps {
  post: Post;
  showThread?: boolean;  // 스레드 뷰에서 세로선 연결
}

export function FeedItem({ post, showThread }: FeedItemProps) {
  if (post.deleted) {
    return <DeletedPostPlaceholder postId={post.id} replyCount={post.replyCount} />;
  }

  // 리포스트: 원본을 가져와서 표시
  if (post.type === 'REPOST') {
    return (
      <div>
        <RepostBanner author={post.author} />
        {/* 원본 게시글을 별도 쿼리로 로드하거나, 서버에서 embed 제공 */}
        <FeedItemContent postId={post.repostId!} />
      </div>
    );
  }

  return (
    <article className="border-b border-border p-4">
      {post.type === 'REPLY' && <ReplyIndicator parentId={post.parentId!} />}

      <div className="flex gap-3">
        <Avatar user={post.author} size={40} />
        <div className="flex-1 min-w-0">
          <PostHeader author={post.author} createdAt={post.createdAt} updatedAt={post.updatedAt} />
          <p className="text-[15px] mt-1 whitespace-pre-wrap break-words">{post.content}</p>
          {post.quotedPost && <QuoteCard post={post.quotedPost} />}
          <PostActions post={post} />
        </div>
      </div>

      {showThread && <div className="ml-5 border-l-2 border-border h-4" />}
    </article>
  );
}
```

### 5.2 PostComposer — 통합 작성기

```
┌─────────────────────────────────────────────────┐
│ [답글 모드]  ↩ @bob에게 답글 작성 중               │
│─────────────────────────────────────────────────│
│ ┌──┐  무슨 생각을 하고 있나요?                     │
│ │🟢│  ____________________________________      │
│ └──┘  ____________________________________      │
│       ____________________________________      │
│                                                   │
│ [인용 모드 — 인용할 게시글 미리보기]                │
│ ┌─────────────────────────────────────────────┐ │
│ │ carol: 여행 사진 공유합니다                    │ │
│ └─────────────────────────────────────────────┘ │
│                                                   │
│  📷 🎥 📍 #        350/500    [게시]              │
└─────────────────────────────────────────────────┘
```

```tsx
// features/post/components/PostComposer.tsx
interface PostComposerProps {
  mode: 'create' | 'reply' | 'quote';
  parentId?: number;        // reply 모드
  quoteId?: number;         // quote 모드
  quotedPost?: Post;        // 인용할 원본 미리보기
  onClose?: () => void;     // 모달에서 사용 시
}
```

**모드별 동작**:

| 모드 | content | parentId | quoteId | 제출 결과 |
|------|---------|----------|---------|-----------|
| create | 필수 | null | null | ORIGINAL 생성 |
| reply | 필수 | 부모 ID | null | REPLY 생성 |
| quote | 필수 | null | 인용 ID | QUOTE 생성 |

> 리포스트는 Composer 를 거치지 않음 — PostActions 의 리포스트 버튼에서 바로 mutation 호출.

### 5.3 PostDetail — 게시글 상세 + 스레드

```
┌─────────────────────────────────────────────────┐
│ ← 뒤로                               게시글     │
│─────────────────────────────────────────────────│
│                                                   │
│ [부모 게시글 (있으면)]                             │
│ ┌──┐ alice_nick                    10분 전        │
│ │  │ 오늘 뭐 먹을까요?                            │
│ └──┘                                              │
│  │ (세로선)                                       │
│  ↓                                                │
│ [현재 게시글 — 강조 표시]                          │
│ ┌──────────────────────────────────────────────┐ │
│ │ ┌──┐ bob_nick                    5분 전       │ │
│ │ │🟢│ 치킨 어때요?                              │ │
│ │ └──┘                                          │ │
│ │ ♡ 3    💬 2    ↗ 0    🔁 1                    │ │
│ └──────────────────────────────────────────────┘ │
│                                                   │
│ [답글 작성기]                                      │
│ ┌──┐ ________________________  [답글]             │
│ └──┘                                              │
│                                                   │
│ [답글 목록]                                        │
│ ┌──┐ carol: 저도 치킨!        3분 전              │
│ └──┘                                              │
│ ┌──┐ dave: 피자가 낫지        1분 전              │
│ └──┘                                              │
│         ↓ 더 보기                                  │
└─────────────────────────────────────────────────┘
```

**데이터 로딩 전략**:

```tsx
// app/post/[id]/page.tsx (Server Component)
export default async function PostPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <div className="max-w-xl mx-auto">
      <PostDetail postId={Number(id)} />
    </div>
  );
}
```

```tsx
// features/post/components/PostDetail.tsx (Client Component)
'use client';

export function PostDetail({ postId }: { postId: number }) {
  const { data: post } = usePostQuery(postId);
  const { data: replies } = useRepliesQuery(postId);

  if (!post) return <FeedItemSkeleton />;

  return (
    <>
      {/* 부모 게시글 (답글인 경우) */}
      {post.parentId && <ParentPostThread parentId={post.parentId} />}

      {/* 현재 게시글 — 확대 표시 */}
      <article className="p-4 border-b-2 border-primary/20">
        <FeedItem post={post} />
      </article>

      {/* 답글 작성기 */}
      <PostComposer mode="reply" parentId={postId} />

      {/* 답글 목록 */}
      <FeedList
        pages={replies?.pages}
        hasNextPage={replies?.hasNextPage}
        fetchNextPage={replies?.fetchNextPage}
      />
    </>
  );
}
```

### 5.4 PostActions — 액션 바

```
♡ 12     💬 3     ↗ 2     🔁 5     📤
좋아요    답글    인용    리포스트    공유
```

| 액션 | 클릭 동작 | 인증 필요 | 옵티미스틱 |
|------|-----------|----------|-----------|
| ♡ 좋아요 | `likeCount ±1` + 아이콘 색상 토글 | ✅ | ✅ (즉시 반영) |
| 💬 답글 | PostComposer 모달 열기 (reply 모드) | ✅ | ❌ (작성 후 반영) |
| ↗ 인용 | PostComposer 모달 열기 (quote 모드) | ✅ | ❌ |
| 🔁 리포스트 | 즉시 리포스트 생성 (확인 팝업) | ✅ | ✅ (repostCount +1) |
| 📤 공유 | Web Share API / 클립보드 복사 | ❌ | ❌ |

```tsx
// features/post/components/PostActions.tsx
export function PostActions({ post }: { post: Post }) {
  const currentUser = useCurrentUser();

  return (
    <div className="flex items-center gap-6 mt-3 text-muted-foreground">
      <ActionButton
        icon={<MessageCircle size={18} />}
        count={post.replyCount}
        onClick={() => openReplyComposer(post.id)}
        label="답글"
      />
      <RepostButton post={post} />
      <QuoteButton post={post} />
      <LikeButton post={post} />
      <ShareButton postId={post.id} />
    </div>
  );
}
```

### 5.5 EditIndicator — 수정 가능 시간

```tsx
// 수정된 게시글: 타임스탬프 옆에 "(수정됨)" 표시
// 수정 가능한 게시글: 메뉴에서 "수정 (15:32까지)" 표시

function EditIndicator({ post }: { post: Post }) {
  const wasEdited = post.updatedAt > post.createdAt && !post.deleted;

  if (wasEdited) {
    return <span className="text-xs text-muted-foreground ml-1">(수정됨)</span>;
  }
  return null;
}
```

### 5.6 PostMenu — 더보기 메뉴 (⋯)

```
┌──────────────────┐
│ ✏️  수정 (15:32까지) │  ← 20분 윈도우 내에만 표시
│ 🗑️  삭제              │  ← 항상 표시
│ 🚩  신고              │  ← 타인 게시글에만
│ 📋  링크 복사          │
└──────────────────┘
```

| 메뉴 항목 | 표시 조건 | 동작 |
|----------|-----------|------|
| 수정 | 본인 게시글 + `editable === true` | 인라인 에디터 또는 모달 |
| 삭제 | 본인 게시글 | 확인 다이얼로그 → deletePost |
| 신고 | 타인 게시글 | 신고 모달 (향후) |
| 링크 복사 | 항상 | 클립보드에 URL 복사 |

### 5.7 DeletedPostPlaceholder

```
┌─────────────────────────────────────────────────┐
│ 🗑️ 이 게시글은 삭제되었습니다.                     │
│                                                   │
│ 💬 5개의 답글                                      │
└─────────────────────────────────────────────────┘
```

- 삭제된 게시글 자리에 표시 (스레드 구조 유지)
- 답글이 있으면 답글 수 표시 + 클릭 시 답글 스레드로 이동
- `bg-muted/30 border-dashed` 스타일로 비활성 느낌

## 6. 페이지 라우트

| 경로 | 설명 | 데이터 |
|------|------|--------|
| `/` | 메인 피드 (전체 최신순) | `useFeedQuery()` |
| `/post/[id]` | 게시글 상세 + 답글 스레드 | `usePostQuery(id)` + `useRepliesQuery(id)` |
| `/[username]` | 프로필 — 게시글 탭 | `useUserPostsQuery(userId)` |

### 라우트 그룹

```
app/
├── (main)/
│   ├── page.tsx                    # 홈 피드
│   └── post/
│       └── [id]/
│           └── page.tsx            # 게시글 상세
```

## 7. 상태 관리 흐름

```
[사용자 액션]
    ↓
[PostComposer / PostActions]
    ↓ mutation 호출
[useCreatePostMutation / useRepostMutation / useDeletePostMutation]
    ↓ onMutate (옵티미스틱)
[TanStack Query 캐시 즉시 갱신]
    ↓ onSuccess
[서버 반영 확인 → invalidateQueries]
    ↓ 실패 시 onError
[롤백 → 이전 캐시 복원]
```

### queryKey 무효화 매트릭스

| 액션 | 무효화 대상 |
|------|------------|
| 게시글 생성 (ORIGINAL) | `['feed']` |
| 답글 생성 (REPLY) | `['feed']`, `['post', parentId]`, `['replies', parentId]` |
| 인용 생성 (QUOTE) | `['feed']`, `['post', quoteId]` |
| 리포스트 (REPOST) | `['feed']`, `['post', repostId]` |
| 게시글 수정 | `['post', id]`, `['feed']` |
| 게시글 삭제 | `['feed']`, `['post', parentId]` (답글인 경우) |

## 8. 인터랙션 애니메이션

| 액션 | 효과 | Tailwind |
|------|------|----------|
| 좋아요 클릭 | 하트 아이콘 스케일 바운스 + 빨간색 전환 | `animate-bounce duration-300` + `text-red-500` |
| 리포스트 클릭 | 아이콘 녹색 전환 | `transition-colors duration-200 text-green-500` |
| 카드 호버 | 배경 미세 변화 | `hover:bg-secondary/30 transition-colors` |
| 삭제 | 카드 페이드아웃 | `animate-out fade-out duration-200` |
| 답글 펼치기 | 세로 슬라이드 다운 | `animate-in slide-in-from-top duration-200` |

## 9. 클라이언트 측 수정 가능 판정

```ts
// features/post/utils/isEditable.ts
import { EDIT_WINDOW_MINUTES } from '../constants';

/**
 * 클라이언트 측 수정 가능 판정.
 * 서버의 `editable` 필드가 최종 권한이지만,
 * UI 에서 실시간 카운트다운을 표시하기 위해 클라이언트에서도 계산.
 */
export function isEditable(post: Post): boolean {
  if (post.type === 'REPOST') return false;
  if (post.deleted) return false;

  const elapsed = Date.now() - post.createdAt.getTime();
  return elapsed < EDIT_WINDOW_MINUTES * 60 * 1000;
}

/** 남은 수정 가능 시간 (초) */
export function editTimeRemaining(post: Post): number {
  const deadline = post.createdAt.getTime() + EDIT_WINDOW_MINUTES * 60 * 1000;
  return Math.max(0, Math.floor((deadline - Date.now()) / 1000));
}
```

> **서버 `editable` vs 클라이언트 계산**: 서버 응답의 `editable` 이 최종 권한. 클라이언트 계산은 실시간 카운트다운 UI 용. 제출 시 서버가 재검증하므로 시간 조작 공격 불가.

## 10. 검증 동기화

| 필드 | 백엔드 | 프론트 zod |
|------|--------|-----------|
| content (일반/답글/인용) | `@Size(max=500) @NotBlank` | `z.string().min(1).max(500)` |
| content (리포스트) | null 허용 | content 미전송 |
| parentId | nullable Long | `z.number().optional()` |
| quoteId | nullable Long | `z.number().optional()` |
| repostId | nullable Long | `z.number().optional()` |

## 11. 향후 확장

- [ ] 좋아요 토글 (`/api/v1/post/{id}/like`) + 옵티미스틱 업데이트
- [ ] 미디어 첨부 (이미지/동영상 업로드 → presigned URL)
- [ ] 해시태그 클릭 → 검색 페이지 이동
- [ ] @멘션 자동완성 (Composer 내 사용자 검색)
- [ ] 수정 이력 보기 모달 (PostEditHistory)
- [ ] 팔로잉 피드 탭 (내가 팔로우한 사람의 글만)
- [ ] 실시간 새 글 알림 (WebSocket / SSE)
- [ ] 무한 스레드 (답글의 답글 재귀 표시)
