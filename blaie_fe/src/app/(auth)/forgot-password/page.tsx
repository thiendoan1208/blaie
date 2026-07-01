import type { Metadata } from "next";
import Link from "next/link";
import { AuthSessionRedirect } from "@/features/auth/ui/auth-session-redirect";
import { AuthShell } from "@/features/auth/ui/auth-shell";
import { ForgotPasswordForm } from "@/features/auth/ui/forgot-password-form";
import { routePaths } from "@/shared/routes/route-paths";

export const metadata: Metadata = {
  title: "Reset password",
  description: "Reset your Blaie password.",
};

export default function ForgotPasswordPage() {
  return (
    <AuthShell
      footer={
        <p>
          Remembered it?{" "}
          <Link
            href={routePaths.login}
            className="font-semibold text-brand-accent underline-offset-4 transition-colors hover:text-brand-accent/80 hover:underline focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            Sign in
          </Link>
        </p>
      }
    >
      <AuthSessionRedirect redirectPath={routePaths.inbox} />
      <ForgotPasswordForm />
    </AuthShell>
  );
}
