"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { useCreatePostMutation } from "@/features/post/hooks/useCreatePostMutation";
import { POST_MAX_LENGTH } from "@/features/post/types";
import { ApiError } from "@/lib/api";

const composerSchema = z.object({
  content: z
    .string()
    .min(1, "내용을 입력해주세요.")
    .max(POST_MAX_LENGTH, `${POST_MAX_LENGTH}자를 초과할 수 없습니다.`),
});

type ComposerValues = z.infer<typeof composerSchema>;

export function PostComposer() {
  const createMutation = useCreatePostMutation();
  const {
    register,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<ComposerValues>({
    resolver: zodResolver(composerSchema),
    defaultValues: { content: "" },
  });

  const content = watch("content");

  const onSubmit = handleSubmit(async (values) => {
    try {
      await createMutation.mutateAsync(values);
      reset();
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors) {
        for (const [field, message] of Object.entries(e.fieldErrors)) {
          setError(field as keyof ComposerValues, { message });
        }
        return;
      }
      if (e instanceof ApiError) {
        setError("root", { message: e.message });
      }
    }
  });

  return (
    <form onSubmit={onSubmit} className="border-border border-b p-4">
      <Textarea
        {...register("content")}
        placeholder="무슨 일이 일어나고 있나요?"
        rows={3}
        aria-invalid={errors.content ? "true" : undefined}
        className="resize-none border-0 shadow-none focus-visible:ring-0"
      />
      {errors.content && <p className="text-destructive mt-1 text-sm">{errors.content.message}</p>}
      {errors.root && <p className="text-destructive mt-1 text-sm">{errors.root.message}</p>}
      <div className="mt-3 flex items-center justify-between">
        <span className="text-muted-foreground text-xs">
          {content.length} / {POST_MAX_LENGTH}
        </span>
        <Button type="submit" disabled={isSubmitting || content.trim().length === 0} size="sm">
          {isSubmitting ? "게시 중..." : "게시"}
        </Button>
      </div>
    </form>
  );
}
