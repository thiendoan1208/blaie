import type { Metadata } from "next";
import Link from "next/link";
import { AuthShell } from "@/features/auth/ui/auth-shell";
import { LoginForm } from "@/features/auth/ui/login-form";
import { AuthSessionRedirect } from "@/features/auth/ui/auth-session-redirect";
import { routePaths } from "@/shared/routes/route-paths";

export const metadata: Metadata = {
  title: "Sign in",
  description: "Sign in to your Blaie account.",
};

type LoginPageProps = {
  searchParams?: Promise<Record<string, string | string[] | undefined>>;
};

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const params = (await searchParams) ?? {};
  const error = firstParam(params.error);
  const nextPath = firstParam(params.next) ?? routePaths.inbox;

  return (
    <AuthShell
      footer={
        <p>
          New to Blaie?{" "}
          <Link
            href={routePaths.register}
            className="font-semibold text-brand-accent underline-offset-4 transition-colors hover:text-brand-accent/80 hover:underline focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            Create an account
          </Link>
        </p>
      }
    >
      <AuthSessionRedirect redirectPath={nextPath} />
      <LoginForm googleAuthFailed={error === "google_auth_failed"} nextPath={nextPath} />
    </AuthShell>
  );
}

function firstParam(value: string | string[] | undefined) {
  return Array.isArray(value) ? value[0] : value;
}
