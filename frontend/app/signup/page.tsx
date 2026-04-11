import Link from "next/link";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { SignupForm } from "@/features/auth/components/SignupForm";

export default function SignupPage() {
  return (
    <main className="mx-auto flex w-full max-w-md flex-1 items-center px-4 py-12">
      <Card className="w-full">
        <CardHeader>
          <CardTitle className="text-center text-2xl">회원가입</CardTitle>
        </CardHeader>
        <CardContent>
          <SignupForm />
          <p className="text-muted-foreground mt-4 text-center text-sm">
            이미 계정이 있으신가요?{" "}
            <Link href="/login" className="text-foreground font-medium underline">
              로그인
            </Link>
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
