import type { NextConfig } from "next";

/**
 * 백엔드(Spring Boot, http://localhost:8080) 를 same-origin 으로 프록시한다.
 *
 * 효과:
 * - 쿠키 세션이 cross-origin 제약 없이 자동 첨부됨 (CORS 불필요)
 * - 클라이언트 fetch 가 `/api/...` 상대 경로로 동작
 * - 운영 환경에서는 리버스 프록시(NGINX 등) 가 동일 역할을 수행
 */
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${BACKEND_URL}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
