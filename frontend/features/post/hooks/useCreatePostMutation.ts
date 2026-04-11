import { useMutation, useQueryClient, type InfiniteData } from "@tanstack/react-query";

import { createPost } from "@/features/post/api";
import { feedQueryKey } from "@/features/post/hooks/useFeedQuery";
import type { PostPage } from "@/features/post/types";

/**
 * 새 글 작성 후 무한쿼리 캐시의 첫 페이지 최상단에 prepend 한다.
 *
 * 전체 invalidate 가 아닌 setQueryData 를 사용하는 이유:
 * - invalidate 는 모든 페이지를 refetch 해 무한 스크롤로 이미 받은 데이터를 모두 다시 받는다 → 네트워크 과부하.
 * - 이미 받은 데이터의 정합성은 setQueryData 로 충분히 유지 가능 (서버 정렬: id DESC).
 */
export function useCreatePostMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createPost,
    onSuccess: (newPost) => {
      queryClient.setQueryData<InfiniteData<PostPage, number>>(feedQueryKey, (old) => {
        if (!old) return old;
        const [firstPage, ...rest] = old.pages;
        if (!firstPage) return old;
        return {
          ...old,
          pages: [
            {
              ...firstPage,
              content: [newPost, ...firstPage.content],
              totalElements: firstPage.totalElements + 1,
            },
            ...rest,
          ],
        };
      });
    },
  });
}
