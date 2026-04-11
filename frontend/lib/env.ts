import { z } from "zod";

/**
 * 클라이언트/서버 모두에서 접근 가능한 공개 환경변수.
 * `NEXT_PUBLIC_` 접두사가 있어야 클라이언트 번들에 포함된다.
 *
 * `NEXT_PUBLIC_API_BASE` 가 비어 있으면 same-origin 으로 동작한다.
 * 기본 동작은 `next.config.ts` 의 rewrites 를 통한 백엔드 프록시 사용을 가정한다.
 */
const publicEnvSchema = z.object({
  NEXT_PUBLIC_API_BASE: z
    .string()
    .default("")
    .refine(
      (v) => v === "" || /^https?:\/\//.test(v),
      "NEXT_PUBLIC_API_BASE must be empty or an http(s) URL",
    ),
});

const parsed = publicEnvSchema.safeParse({
  NEXT_PUBLIC_API_BASE: process.env.NEXT_PUBLIC_API_BASE,
});

if (!parsed.success) {
  console.error("❌ Invalid environment variables:", parsed.error.flatten().fieldErrors);
  throw new Error("Invalid environment variables");
}

export const env = parsed.data;
