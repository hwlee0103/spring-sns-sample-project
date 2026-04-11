"use client";

import { useCurrentUser } from "@/features/auth/hooks/useCurrentUser";
import { PostComposer } from "@/features/post/components/PostComposer";

/**
 * PostComposer 를 로그인 상태에서만 노출한다.
 * 비로그인이면 안내 문구만 표시.
 */
export function ComposerSection() {
  const { data: currentUser, isPending } = useCurrentUser();

  if (isPending) return null;
  if (!currentUser) {
    return (
      <p className="border-border text-muted-foreground border-b p-4 text-sm">
        게시글을 작성하려면 로그인이 필요합니다.
      </p>
    );
  }

  return <PostComposer />;
}
