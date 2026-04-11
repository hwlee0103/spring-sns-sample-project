import Link from "next/link";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { LoginForm } from "@/features/auth/components/LoginForm";

export default function LoginPage() {
  return (
    <main className="mx-auto flex w-full max-w-md flex-1 items-center px-4 py-12">
      <Card className="w-full">
        <CardHeader>
          <CardTitle className="text-center text-2xl">로그인</CardTitle>
        </CardHeader>
        <CardContent>
          <LoginForm />
          <p className="text-muted-foreground mt-4 text-center text-sm">
            계정이 없으신가요?{" "}
            <Link href="/signup" className="text-foreground font-medium underline">
              가입하기
            </Link>
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
