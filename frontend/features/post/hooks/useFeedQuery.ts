import { useInfiniteQuery } from "@tanstack/react-query";

import { fetchFeed } from "@/features/post/api";

export const FEED_PAGE_SIZE = 20;

/**
 * queryKey 에 size 를 포함하여 향후 정렬/사이즈 변경 시 캐시가 분리되도록 한다.
 * `as const` 로 readonly tuple 처리해 setQueryData 등에서 키 비교 일관성 보장.
 */
export const feedQueryKey = ["feed", { size: FEED_PAGE_SIZE }] as const;

/**
 * 무한 스크롤 피드 쿼리.
 * 마지막 페이지가 아니면 다음 page 인덱스를 반환한다.
 */
export function useFeedQuery() {
  return useInfiniteQuery({
    queryKey: feedQueryKey,
    queryFn: ({ pageParam }) => fetchFeed({ page: pageParam, size: FEED_PAGE_SIZE }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.page + 1),
  });
}
