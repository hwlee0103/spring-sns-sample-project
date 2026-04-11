"use client";

import { useEffect, useRef } from "react";

import { Button } from "@/components/ui/button";
import { FeedItem } from "@/features/post/components/FeedItem";
import { useFeedQuery } from "@/features/post/hooks/useFeedQuery";

/**
 * 피드 무한 스크롤 리스트.
 *
 * Observer 는 mount 시 1회만 등록하고, 콜백 내부에서 ref 를 통해 최신 상태를 읽는다.
 * 이렇게 하지 않으면 `isFetchingNextPage` 토글마다 effect 가 disconnect/re-observe 되어
 * 사용자가 빠르게 스크롤할 때 sentinel 을 놓치고 페이지 로딩이 끊긴다.
 */
export function FeedList() {
  const { data, isPending, isError, error, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useFeedQuery();

  const sentinelRef = useRef<HTMLDivElement | null>(null);

  // 콜백 내부에서 항상 최신 값을 읽기 위한 ref 들
  const stateRef = useRef({ hasNextPage, isFetchingNextPage });
  const fetchNextPageRef = useRef(fetchNextPage);
  stateRef.current = { hasNextPage, isFetchingNextPage };
  fetchNextPageRef.current = fetchNextPage;

  useEffect(() => {
    const node = sentinelRef.current;
    if (!node) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const first = entries[0];
        if (!first?.isIntersecting) return;
        const { hasNextPage: hasNext, isFetchingNextPage: isFetching } = stateRef.current;
        if (hasNext && !isFetching) {
          void fetchNextPageRef.current();
        }
      },
      { rootMargin: "200px" },
    );

    observer.observe(node);
    return () => observer.disconnect();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (isPending) {
    return <p className="text-muted-foreground p-4 text-sm">불러오는 중...</p>;
  }

  if (isError) {
    return (
      <p className="text-destructive p-4 text-sm">
        에러: {error instanceof Error ? error.message : "알 수 없는 오류"}
      </p>
    );
  }

  const allPosts = data.pages.flatMap((page) => page.content);
  if (allPosts.length === 0) {
    return <p className="text-muted-foreground p-8 text-center text-sm">아직 게시글이 없습니다.</p>;
  }

  return (
    <div>
      {allPosts.map((post) => (
        <FeedItem key={post.id} post={post} />
      ))}
      <div ref={sentinelRef} className="py-4 text-center">
        {isFetchingNextPage && (
          <span className="text-muted-foreground text-sm">더 불러오는 중...</span>
        )}
        {!hasNextPage && allPosts.length > 0 && (
          <span className="text-muted-foreground text-xs">마지막 게시글입니다.</span>
        )}
        {hasNextPage && !isFetchingNextPage && (
          <Button variant="ghost" size="sm" onClick={() => void fetchNextPage()}>
            더 보기
          </Button>
        )}
      </div>
    </div>
  );
}
