import { api } from "@/lib/api";
import { UserSchema, pageResponseSchema, type User } from "@/features/user/types";

const UserPageSchema = pageResponseSchema(UserSchema);

interface FetchUsersParams {
  page?: number;
  size?: number;
}

/** 사용자 목록 조회 (페이징). */
export function fetchUsers({ page = 0, size = 20 }: FetchUsersParams = {}) {
  return api.get("/api/user", {
    schema: UserPageSchema,
    query: { page, size },
  });
}

/** 단일 사용자 조회. */
export function fetchUser(id: number) {
  return api.get(`/api/user/${id}`, { schema: UserSchema });
}

interface RegisterUserBody {
  email: string;
  password: string;
  nickname: string;
}

/** 회원가입. */
export function registerUser(body: RegisterUserBody): Promise<User> {
  return api.post("/api/user", { body, schema: UserSchema });
}
