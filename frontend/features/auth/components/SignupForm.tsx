"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { z } from "zod";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useLoginMutation } from "@/features/auth/hooks/useLoginMutation";
import { registerUser } from "@/features/user/api";
import { ApiError } from "@/lib/api";

// 백엔드 UserCreateRequest 검증과 동일한 규칙
const signupSchema = z.object({
  email: z.string().email("이메일 형식이 올바르지 않습니다."),
  password: z
    .string()
    .min(8, "비밀번호는 8자 이상이어야 합니다.")
    .max(64, "비밀번호는 64자 이하여야 합니다."),
  nickname: z
    .string()
    .min(2, "닉네임은 2자 이상이어야 합니다.")
    .max(20, "닉네임은 20자 이하여야 합니다."),
});

type SignupValues = z.infer<typeof signupSchema>;

export function SignupForm() {
  const router = useRouter();
  const loginMutation = useLoginMutation();
  const signupMutation = useMutation({ mutationFn: registerUser });

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { email: "", password: "", nickname: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    try {
      await signupMutation.mutateAsync(values);
      // 가입 후 자동 로그인
      await loginMutation.mutateAsync({ email: values.email, password: values.password });
      router.push("/");
      router.refresh();
    } catch (e) {
      if (e instanceof ApiError && e.fieldErrors) {
        for (const [field, message] of Object.entries(e.fieldErrors)) {
          setError(field as keyof SignupValues, { message });
        }
        return;
      }
      if (e instanceof ApiError) {
        setError("root", { message: e.message });
      }
    }
  });

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <Label htmlFor="signup-email">이메일</Label>
        <Input
          id="signup-email"
          type="email"
          autoComplete="email"
          aria-invalid={errors.email ? "true" : undefined}
          {...register("email")}
        />
        {errors.email && <p className="text-destructive text-sm">{errors.email.message}</p>}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="signup-nickname">닉네임</Label>
        <Input
          id="signup-nickname"
          autoComplete="nickname"
          aria-invalid={errors.nickname ? "true" : undefined}
          {...register("nickname")}
        />
        {errors.nickname && <p className="text-destructive text-sm">{errors.nickname.message}</p>}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="signup-password">비밀번호</Label>
        <Input
          id="signup-password"
          type="password"
          autoComplete="new-password"
          aria-invalid={errors.password ? "true" : undefined}
          {...register("password")}
        />
        {errors.password && <p className="text-destructive text-sm">{errors.password.message}</p>}
      </div>

      {errors.root && <p className="text-destructive text-sm">{errors.root.message}</p>}

      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? "가입 중..." : "가입하기"}
      </Button>
    </form>
  );
}
