import { useInfiniteQuery } from "@tanstack/react-query";

import { fetchFeed } from "@/features/post/api";

const FEED_PAGE_SIZE = 20;

export const feedQueryKey = ["feed"] as const;

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
