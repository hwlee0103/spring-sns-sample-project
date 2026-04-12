"use client";

import Link from "next/link";

import { Button, buttonVariants } from "@/components/ui/button";
import { useCurrentUser } from "@/features/auth/hooks/useCurrentUser";
import { useLogoutMutation } from "@/features/auth/hooks/useLogoutMutation";

/**
 * 전역 헤더.
 *
 * <p>TODO(#37): 완전한 깜빡임 제거는 layout.tsx 의 Server Component 에서 next/headers 의 cookies() 를
 * 포워딩하여 prefetch + HydrationBoundary 패턴으로 처리해야 한다. 현재는 isPending 시 skeleton 으로
 * 레이아웃을 안정화한다.
 */
export function Header() {
  const { data: currentUser, isPending } = useCurrentUser();
  const logoutMutation = useLogoutMutation();

  return (
    <header className="border-border bg-background sticky top-0 z-10 border-b backdrop-blur">
      <div className="mx-auto flex h-14 max-w-2xl items-center justify-between px-4">
        <Link href="/" className="text-lg font-bold tracking-tight">
          SNS Sample
        </Link>
        <nav className="flex items-center gap-2" aria-busy={isPending}>
          {isPending ? (
            // 레이아웃 안정화용 skeleton — 비로그인/로그인 상태가 결정될 때까지 자리만 유지
            <div
              className="bg-muted h-7 w-24 animate-pulse rounded"
              aria-label="사용자 상태 로딩 중"
            />
          ) : currentUser ? (
            <>
              <span className="text-muted-foreground text-sm">{currentUser.nickname}</span>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => logoutMutation.mutate()}
                disabled={logoutMutation.isPending}
              >
                로그아웃
              </Button>
            </>
          ) : (
            <>
              <Link href="/login" className={buttonVariants({ variant: "ghost", size: "sm" })}>
                로그인
              </Link>
              <Link href="/signup" className={buttonVariants({ size: "sm" })}>
                가입
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
