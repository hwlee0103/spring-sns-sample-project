import { api, ApiError } from "@/lib/api";
import { UserSchema, type User } from "@/features/user/types";

interface LoginBody {
  email: string;
  password: string;
}

/** 로그인 — 성공 시 세션 쿠키가 자동으로 설정된다. */
export function login(body: LoginBody): Promise<User> {
  return api.post("/api/auth/login", { body, schema: UserSchema });
}

/** 로그아웃 — 서버에서 세션을 무효화한다. */
export function logout(): Promise<void> {
  return api.post("/api/auth/logout");
}

/**
 * 현재 로그인된 사용자 조회.
 * 비로그인(401) 인 경우 `null` 을 반환한다.
 */
export async function fetchCurrentUser(): Promise<User | null> {
  try {
    return await api.get("/api/auth/me", { schema: UserSchema });
  } catch (e) {
    if (e instanceof ApiError && e.isUnauthorized) return null;
    throw e;
  }
}
