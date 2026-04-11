import { z } from "zod";

/**
 * 백엔드 `UserResponse` 와 1:1 매핑.
 *
 * 백엔드 record:
 *   public record UserResponse(Long id, String email, String nickname)
 */
export const UserSchema = z.object({
  id: z.number(),
  email: z.string().email(),
  nickname: z.string(),
});

export type User = z.infer<typeof UserSchema>;

/**
 * 백엔드 `PageResponse<T>` 와 1:1 매핑.
 * 다른 도메인 목록 응답에서도 재사용한다 (`PageResponseSchema(PostSchema)` 등).
 */
export function pageResponseSchema<T extends z.ZodTypeAny>(item: T) {
  return z.object({
    content: z.array(item),
    page: z.number(),
    size: z.number(),
    totalElements: z.number(),
    totalPages: z.number(),
    first: z.boolean(),
    last: z.boolean(),
  });
}
