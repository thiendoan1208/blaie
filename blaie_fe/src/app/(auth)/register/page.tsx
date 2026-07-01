import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell } from "@/features/auth/ui/auth-shell";
import { AuthSessionRedirect } from "@/features/auth/ui/auth-session-redirect";
import { RegisterForm } from "@/features/auth/ui/register-form";
import { routePaths } from "@/shared/routes/route-paths";

export const metadata: Metadata = {
  title: "Create account",
  description: "Create your Blaie account.",
};

export default function RegisterPage() {
  return (
    <AuthShell
      footer={
        <p>
          Already have an account?{" "}
          <Link
            href={routePaths.login}
            className="font-semibold text-brand-accent underline-offset-4 transition-colors hover:text-brand-accent/80 hover:underline focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            Sign in
          </Link>
        </p>
      }
    >
      <AuthSessionRedirect />
      <RegisterForm />
    </AuthShell>
  );
}
