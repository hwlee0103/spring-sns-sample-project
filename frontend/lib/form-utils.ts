import type { FieldValues, Path, UseFormSetError } from "react-hook-form";

import { ApiError } from "@/lib/api";

/**
 * 백엔드 ApiError(특히 fieldErrors) 를 react-hook-form 의 setError 에 안전하게 매핑한다.
 *
 * 포인트:
 * - 화이트리스트 필드만 폼 필드에 매핑한다 → 백엔드가 알 수 없는 필드명을 반환해도 silent fail 하지 않음.
 * - 화이트리스트 외 필드는 `root` 에 합쳐서 표시.
 * - fieldErrors 가 없는 일반 ApiError 는 메시지를 `root` 에 표시.
 *
 * 사용 예:
 *   try { await mutation.mutateAsync(values); }
 *   catch (e) {
 *     applyApiErrors(setError, e, ['email', 'password', 'nickname'] as const);
 *   }
 */
export function applyApiErrors<TValues extends FieldValues>(
  setError: UseFormSetError<TValues>,
  error: unknown,
  knownFields: ReadonlyArray<Path<TValues>>,
): void {
  if (!(error instanceof ApiError)) {
    setError("root" as Path<TValues>, {
      message: error instanceof Error ? error.message : "알 수 없는 오류가 발생했습니다.",
    });
    return;
  }

  if (!error.fieldErrors) {
    setError("root" as Path<TValues>, { message: error.message });
    return;
  }

  const knownSet = new Set<string>(knownFields as readonly string[]);
  const unknown: string[] = [];

  for (const [field, message] of Object.entries(error.fieldErrors)) {
    if (knownSet.has(field)) {
      setError(field as Path<TValues>, { message });
    } else {
      unknown.push(`${field}: ${message}`);
    }
  }

  if (unknown.length > 0) {
    setError("root" as Path<TValues>, { message: unknown.join(" / ") });
  }
}
