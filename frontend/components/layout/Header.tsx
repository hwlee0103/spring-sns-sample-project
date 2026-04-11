"use client";

import Link from "next/link";

import { Button, buttonVariants } from "@/components/ui/button";
import { useCurrentUser } from "@/features/auth/hooks/useCurrentUser";
import { useLogoutMutation } from "@/features/auth/hooks/useLogoutMutation";

export function Header() {
  const { data: currentUser, isPending } = useCurrentUser();
  const logoutMutation = useLogoutMutation();

  return (
    <header className="border-border bg-background sticky top-0 z-10 border-b backdrop-blur">
      <div className="mx-auto flex h-14 max-w-2xl items-center justify-between px-4">
        <Link href="/" className="text-lg font-bold tracking-tight">
          SNS Sample
        </Link>
        <nav className="flex items-center gap-2">
          {isPending ? null : currentUser ? (
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
