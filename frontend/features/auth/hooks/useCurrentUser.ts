import { useQuery } from "@tanstack/react-query";

import { fetchCurrentUser } from "@/features/auth/api";

export const currentUserQueryKey = ["auth", "me"] as const;

/**
 * 현재 로그인된 사용자.
 * 비로그인이면 `data === null`.
 * 헤더, 사이드바, 작성 버튼 노출 여부 등에서 단일 소스로 사용한다.
 */
export function useCurrentUser() {
  return useQuery({
    queryKey: currentUserQueryKey,
    queryFn: fetchCurrentUser,
    staleTime: 5 * 60 * 1000, // 5분
  });
}
