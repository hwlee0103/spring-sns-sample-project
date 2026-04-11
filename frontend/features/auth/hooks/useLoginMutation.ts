import { useMutation, useQueryClient } from "@tanstack/react-query";

import { login } from "@/features/auth/api";
import { currentUserQueryKey } from "@/features/auth/hooks/useCurrentUser";

export function useLoginMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: login,
    onSuccess: (user) => {
      // 로그인 직후 me 캐시를 즉시 채워 헤더 등이 깜빡이지 않도록 한다.
      queryClient.setQueryData(currentUserQueryKey, user);
    },
  });
}
