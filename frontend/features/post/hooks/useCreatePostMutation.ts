import { useMutation, useQueryClient } from "@tanstack/react-query";

import { createPost } from "@/features/post/api";
import { feedQueryKey } from "@/features/post/hooks/useFeedQuery";

export function useCreatePostMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createPost,
    onSuccess: () => {
      // 새 글이 피드 최상단에 보이도록 무효화 → 첫 페이지 재요청
      queryClient.invalidateQueries({ queryKey: feedQueryKey });
    },
  });
}
