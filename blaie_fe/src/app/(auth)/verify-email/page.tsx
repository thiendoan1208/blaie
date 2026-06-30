import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";
import { UserProvider } from "@/features/auth/model/user-context";
import { AuthShell } from "@/features/auth/ui/auth-shell";
import { VerifyEmailPanel } from "@/features/auth/ui/verify-email-panel";
import { routePaths } from "@/shared/routes/route-paths";

export const metadata: Metadata = {
  title: "Verify email",
  description: "Verify your Blaie account email.",
};

export default function VerifyEmailPage() {
  return (
    <AuthShell
      footer={
        <p>
          Wrong account?{" "}
          <Link
            href={routePaths.login}
            className="font-semibold text-brand-accent underline-offset-4 transition-colors hover:text-brand-accent/80 hover:underline focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            Sign in again
          </Link>
        </p>
      }
    >
      <Suspense
        fallback={
          <div className="flex min-h-48 items-center justify-center text-sm text-muted-foreground">
            Loading...
          </div>
        }
      >
        <UserProvider>
          <VerifyEmailPanel />
        </UserProvider>
      </Suspense>
    </AuthShell>
  );
}
