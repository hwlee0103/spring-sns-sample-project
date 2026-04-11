import { z, type ZodType } from "zod";

import { env } from "@/lib/env";

/**
 * 백엔드 검증 실패 응답의 표준 스키마.
 * Spring `GlobalExceptionHandler.handleValidationException` 와 일치한다.
 *
 * 예: { "message": "요청 값이 올바르지 않습니다.", "errors": { "email": "..." } }
 */
const ValidationErrorBodySchema = z.object({
  message: z.string().optional(),
  errors: z.record(z.string(), z.string()).optional(),
});

/**
 * 백엔드 도메인 예외 응답의 표준 스키마.
 * Spring `GlobalExceptionHandler.handleDomainException` 와 일치한다.
 *
 * 예: { "message": "이미 존재하는 이메일입니다: a@b.com" }
 */
const DomainErrorBodySchema = z.object({
  message: z.string(),
});

export type FieldErrors = Record<string, string>;

/**
 * 백엔드 호출 중 발생한 모든 에러의 공통 타입.
 *
 * - `status`: HTTP 상태 코드 (네트워크 오류 시 0)
 * - `message`: 사용자에게 표시할 수 있는 에러 메시지
 * - `fieldErrors`: 백엔드 Bean Validation 실패 시 필드별 메시지 (`react-hook-form.setError` 로 매핑)
 */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors?: FieldErrors;

  constructor(message: string, status: number, fieldErrors?: FieldErrors) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }

  /** 백엔드 검증 실패(400 + errors 객체)인지 여부. */
  get isValidationError(): boolean {
    return this.status === 400 && this.fieldErrors !== undefined;
  }

  /** 인증 필요(401)인지 여부. */
  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  /** 리소스 없음(404)인지 여부. */
  get isNotFound(): boolean {
    return this.status === 404;
  }
}

type QueryValue = string | number | boolean | undefined | null;
type Query = Record<string, QueryValue | QueryValue[]>;

interface RequestOptions<TSchema extends ZodType> {
  /** zod 스키마 — 응답 본문 런타임 검증. 미지정 시 `undefined` 반환. */
  schema?: TSchema;
  /** 쿼리스트링 파라미터. 배열은 동일 키로 반복된다. */
  query?: Query;
  /** 추가 fetch 옵션. `credentials`, `headers`, `body` 는 wrapper 가 관리. */
  init?: Omit<RequestInit, "body" | "credentials">;
  /** 다음 캐싱 옵션. 미지정 시 no-store(매 요청 fresh). */
  cache?: RequestCache;
  /** Next.js revalidate 옵션 (Server Component fetch 메모이제이션용). */
  next?: { revalidate?: number | false; tags?: string[] };
}

interface MutationOptions<TSchema extends ZodType> extends RequestOptions<TSchema> {
  /** 요청 본문. 자동으로 JSON 직렬화된다. */
  body?: unknown;
}

function buildUrl(path: string, query?: Query): string {
  const base = env.NEXT_PUBLIC_API_BASE.replace(/\/$/, "");
  const normalized = path.startsWith("/") ? path : `/${path}`;
  if (!query) return `${base}${normalized}`;

  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null) continue;
    if (Array.isArray(value)) {
      for (const v of value) {
        if (v !== undefined && v !== null) params.append(key, String(v));
      }
    } else {
      params.append(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `${base}${normalized}?${qs}` : `${base}${normalized}`;
}

async function parseError(response: Response): Promise<ApiError> {
  let raw: unknown;
  try {
    raw = await response.json();
  } catch {
    return new ApiError(`HTTP ${response.status}`, response.status);
  }

  // 검증 실패 응답 우선 매칭
  const validation = ValidationErrorBodySchema.safeParse(raw);
  if (validation.success && validation.data.errors) {
    return new ApiError(
      validation.data.message ?? "요청 값이 올바르지 않습니다.",
      response.status,
      validation.data.errors,
    );
  }

  const domain = DomainErrorBodySchema.safeParse(raw);
  if (domain.success) {
    return new ApiError(domain.data.message, response.status);
  }

  return new ApiError(`HTTP ${response.status}`, response.status);
}

async function request<TSchema extends ZodType | undefined>(
  method: string,
  path: string,
  options: MutationOptions<NonNullable<TSchema>> = {},
): Promise<TSchema extends ZodType<infer Out> ? Out : void> {
  const { schema, query, init, body, cache, next } = options;

  const headers = new Headers(init?.headers);
  if (body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (!headers.has("Accept")) {
    headers.set("Accept", "application/json");
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      ...init,
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      credentials: "include", // 쿠키 세션 자동 첨부
      cache: cache ?? "no-store",
      next,
    });
  } catch (e) {
    throw new ApiError(
      e instanceof Error ? e.message : "Network error",
      0,
    );
  }

  if (!response.ok) {
    throw await parseError(response);
  }

  if (response.status === 204 || !schema) {
    // schema 미지정 시 void 반환
    return undefined as TSchema extends ZodType<infer Out> ? Out : void;
  }

  let raw: unknown;
  try {
    raw = await response.json();
  } catch {
    throw new ApiError("Invalid JSON response", response.status);
  }

  const parsed = schema.safeParse(raw);
  if (!parsed.success) {
    if (process.env.NODE_ENV !== "production") {
      console.error("[api] response schema mismatch", {
        path,
        issues: parsed.error.issues,
      });
    }
    throw new ApiError("응답 형식이 올바르지 않습니다.", response.status);
  }
  return parsed.data as TSchema extends ZodType<infer Out> ? Out : void;
}

export const api = {
  get<TSchema extends ZodType | undefined = undefined>(
    path: string,
    options?: RequestOptions<NonNullable<TSchema>>,
  ) {
    return request<TSchema>("GET", path, options);
  },
  post<TSchema extends ZodType | undefined = undefined>(
    path: string,
    options?: MutationOptions<NonNullable<TSchema>>,
  ) {
    return request<TSchema>("POST", path, options);
  },
  put<TSchema extends ZodType | undefined = undefined>(
    path: string,
    options?: MutationOptions<NonNullable<TSchema>>,
  ) {
    return request<TSchema>("PUT", path, options);
  },
  patch<TSchema extends ZodType | undefined = undefined>(
    path: string,
    options?: MutationOptions<NonNullable<TSchema>>,
  ) {
    return request<TSchema>("PATCH", path, options);
  },
  delete<TSchema extends ZodType | undefined = undefined>(
    path: string,
    options?: MutationOptions<NonNullable<TSchema>>,
  ) {
    return request<TSchema>("DELETE", path, options);
  },
};
