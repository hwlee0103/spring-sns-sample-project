import { QueryClient, isServer } from "@tanstack/react-query";

import { ApiError } from "@/lib/api";

/**
 * TanStack Query 의 기본 옵션을 통일한다.
 * - 인증 실패(401) 또는 클라이언트 에러(4xx) 는 재시도하지 않는다.
 * - 5xx 만 1회 재시도 — SNS 피드는 빠른 실패가 사용자 경험상 더 낫다.
 */
function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000, // 1분 — SNS 피드 디폴트
        gcTime: 5 * 60 * 1000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) => {
          if (error instanceof ApiError) {
            if (error.status >= 400 && error.status < 500) return false;
          }
          return failureCount < 1;
        },
      },
      mutations: {
        retry: false,
      },
    },
  });
}

let browserQueryClient: QueryClient | undefined;

/**
 * 서버: 매 요청 새로운 client (요청 간 캐시 격리)
 * 클라이언트: 모듈 단위 싱글톤 (React 트리 전체에서 공유)
 */
export function getQueryClient(): QueryClient {
  if (isServer) return makeQueryClient();
  if (!browserQueryClient) browserQueryClient = makeQueryClient();
  return browserQueryClient;
}
