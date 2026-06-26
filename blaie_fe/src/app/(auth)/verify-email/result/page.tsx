import type { Metadata } from "next";
import { Suspense } from "react";
import { AuthShell } from "@/features/auth/ui/auth-shell";
import { VerifyEmailResultPanel } from "@/features/auth/ui/verify-email-result-panel";

export const metadata: Metadata = {
  title: "Email verification result",
  description: "View your Blaie email verification result.",
};

export default function VerifyEmailResultPage() {
  return (
    <AuthShell footer={<></>}>
      <Suspense
        fallback={
          <div className="flex min-h-48 items-center justify-center text-sm text-stone-gray">
            Loading...
          </div>
        }
      >
        <VerifyEmailResultPanel />
      </Suspense>
    </AuthShell>
  );
}
