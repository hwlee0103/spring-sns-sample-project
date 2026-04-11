import { api } from "@/lib/api";
import { PostPageSchema, PostSchema, type Post } from "@/features/post/types";

interface FetchFeedParams {
  page?: number;
  size?: number;
}

/** 피드 페이지네이션 조회. */
export function fetchFeed({ page = 0, size = 20 }: FetchFeedParams = {}) {
  return api.get("/api/post", {
    schema: PostPageSchema,
    query: { page, size },
  });
}

/** 단일 게시글 조회. */
export function fetchPost(id: number): Promise<Post> {
  return api.get(`/api/post/${id}`, { schema: PostSchema });
}

/** 게시글 작성. */
export function createPost(body: { content: string }): Promise<Post> {
  return api.post("/api/post", { body, schema: PostSchema });
}

/** 게시글 수정. */
export function updatePost(id: number, body: { content: string }): Promise<Post> {
  return api.put(`/api/post/${id}`, { body, schema: PostSchema });
}

/** 게시글 삭제. */
export function deletePost(id: number): Promise<void> {
  return api.delete(`/api/post/${id}`);
}
