"use client";

import { AlertTriangle, ArrowRight, CheckCircle2, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { routePaths } from "@/shared/routes/route-paths";

export function VerifyEmailResultPanel() {
  const searchParams = useSearchParams();
  const isSuccess = searchParams.get("emailVerified") === "1";

  if (isSuccess) {
    return (
      <div className="space-y-7">
        <div className="space-y-3">
          <div className="flex size-11 items-center justify-center rounded-lg border border-emerald-400/25 bg-emerald-400/10 text-emerald-300">
            <CheckCircle2 className="size-5" aria-hidden="true" />
          </div>
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase text-stone-gray">
              Email verified
            </p>
            <h1 className="text-3xl font-semibold tracking-normal text-ivory-text">
              You are ready
            </h1>
            <p className="text-sm leading-6 text-stone-gray">
              Your email has been verified. You can continue to Blaie now.
            </p>
          </div>
        </div>

        <Button asChild className="h-11 w-full bg-primary text-primary-foreground hover:bg-primary/80">
          <Link href={routePaths.inbox} className="flex w-full items-center justify-center gap-2 text-primary-foreground hover:text-primary-foreground">
            Go to Inbox
            <ArrowRight className="size-4" aria-hidden="true" />
          </Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-7">
      <div className="space-y-3">
        <div className="flex size-11 items-center justify-center rounded-lg border border-amber-300/25 bg-amber-300/10 text-amber-200">
          <AlertTriangle className="size-5" aria-hidden="true" />
        </div>
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase text-stone-gray">
            Verification failed
          </p>
          <h1 className="text-3xl font-semibold tracking-normal text-ivory-text">
            Link expired
          </h1>
          <p className="text-sm leading-6 text-stone-gray">
            This verification link is invalid, expired, or already used. Send a fresh link from the verification screen.
          </p>
        </div>
      </div>

      <Button asChild className="h-11 w-full bg-primary text-primary-foreground hover:bg-primary/80">
        <Link href={routePaths.verifyEmail} className="flex w-full items-center justify-center gap-2 text-primary-foreground hover:text-primary-foreground">
          Back to verification
          <RefreshCw className="size-4" aria-hidden="true" />
        </Link>
      </Button>
    </div>
  );
}
