import { useMutation, useQueryClient } from "@tanstack/react-query";

import { logout } from "@/features/auth/api";
import { currentUserQueryKey } from "@/features/auth/hooks/useCurrentUser";

export function useLogoutMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.setQueryData(currentUserQueryKey, null);
      // 사용자별로 격리되어야 하는 캐시를 모두 무효화한다.
      queryClient.invalidateQueries({ queryKey: ["feed"] });
    },
  });
}
