import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell } from "@/features/auth/ui/auth-shell";
import { LoginForm } from "@/features/auth/ui/login-form";
import { routePaths } from "@/shared/routes/route-paths";

export const metadata: Metadata = {
  title: "Sign in",
  description: "Sign in to your Blaie account.",
};

export default function LoginPage() {
  return (
    <AuthShell
      footer={
        <p>
          New to Blaie?{" "}
          <Link
            href={routePaths.register}
            className="font-semibold text-dust-purple underline-offset-4 transition-colors hover:text-dust-purple/80 hover:underline focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-dust-purple"
          >
            Create an account
          </Link>
        </p>
      }
    >
      <LoginForm />
    </AuthShell>
  );
}
