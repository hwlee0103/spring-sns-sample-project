"use client";

import { useEffect, useRef } from "react";

import { Button } from "@/components/ui/button";
import { FeedItem } from "@/features/post/components/FeedItem";
import { useFeedQuery } from "@/features/post/hooks/useFeedQuery";

/**
 * 피드 무한 스크롤 리스트.
 * Intersection Observer 로 마지막 요소가 보이면 다음 페이지를 자동 로드한다.
 */
export function FeedList() {
  const { data, isPending, isError, error, fetchNextPage, hasNextPage, isFetchingNextPage } =
    useFeedQuery();

  const sentinelRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const node = sentinelRef.current;
    if (!node || !hasNextPage) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const first = entries[0];
        if (first?.isIntersecting && !isFetchingNextPage) {
          void fetchNextPage();
        }
      },
      { rootMargin: "200px" },
    );

    observer.observe(node);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

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
