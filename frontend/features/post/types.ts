import { z } from "zod";

import { UserSchema, pageResponseSchema } from "@/features/user/types";

/**
 * 백엔드 `PostResponse` 와 1:1 매핑.
 *
 * record PostResponse(Long id, String content, UserResponse author, Instant createdAt)
 */
export const PostSchema = z.object({
  id: z.number(),
  content: z.string(),
  author: UserSchema,
  createdAt: z.coerce.date(),
});

export type Post = z.infer<typeof PostSchema>;

export const PostPageSchema = pageResponseSchema(PostSchema);
export type PostPage = z.infer<typeof PostPageSchema>;

export const POST_MAX_LENGTH = 500;
