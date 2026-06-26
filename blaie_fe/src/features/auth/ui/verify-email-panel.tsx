"use client";

import { Loader2, LogOut, MailCheck, RefreshCw } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { defaultAuthenticatedRoute, routePaths } from "@/shared/routes/route-paths";
import { useCurrentUserQuery } from "../model/auth.queries";
import {
  useLogoutMutation,
  useResendEmailVerificationMutation,
} from "../model/auth.mutations";

export function VerifyEmailPanel() {
  const router = useRouter();
  const { data: user, isError, isPending } = useCurrentUserQuery();
  const resendMutation = useResendEmailVerificationMutation();
  const logoutMutation = useLogoutMutation();

  const statusText = useMemo(() => {
    if (resendMutation.isSuccess) {
      return "Verification email sent.";
    }
    return "We sent a verification link to your email.";
  }, [resendMutation.isSuccess]);

  useEffect(() => {
    if (isError) {
      const loginUrl = new URL(routePaths.login, window.location.origin);
      loginUrl.searchParams.set("next", routePaths.verifyEmail);
      window.location.assign(loginUrl.toString());
    }
  }, [isError]);

  useEffect(() => {
    if (user?.emailVerified) {
      router.replace(defaultAuthenticatedRoute);
    }
  }, [router, user]);

  async function resendVerification() {
    try {
      await resendMutation.mutateAsync();
      toast.success("Verification email sent.");
    } catch {
      toast.error("Could not send verification email.");
    }
  }

  async function logout() {
    try {
      await logoutMutation.mutateAsync();
    } finally {
      router.replace(routePaths.login);
      router.refresh();
    }
  }

  if (isPending || isError || user?.emailVerified) {
    return (
      <div className="flex min-h-48 items-center justify-center text-sm text-stone-gray">
        Loading...
      </div>
    );
  }

  return (
    <div className="space-y-7">
      <div className="space-y-3">
        <div className="flex size-11 items-center justify-center rounded-lg border border-dust-purple/25 bg-dust-purple/10 text-dust-purple">
          <MailCheck className="size-5" aria-hidden="true" />
        </div>
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase text-stone-gray">
            Verify email
          </p>
          <h1 className="text-3xl font-semibold tracking-normal text-ivory-text">
            Check your email
          </h1>
          <p className="text-sm leading-6 text-stone-gray">
            {statusText}
          </p>
        </div>
      </div>

      <div className="rounded-lg border border-graphite-border bg-graphite-panel/60 px-4 py-3 text-sm text-ivory-text">
        {user.email}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Button
          type="button"
          disabled={resendMutation.isPending}
          onClick={resendVerification}
          className="h-11"
        >
          {resendMutation.isPending ? (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          ) : (
            <RefreshCw className="size-4" aria-hidden="true" />
          )}
          Resend
        </Button>
        <Button
          type="button"
          variant="outline"
          disabled={logoutMutation.isPending}
          onClick={logout}
          className="h-11 border-graphite-border bg-transparent text-ivory-text hover:bg-graphite-panel"
        >
          {logoutMutation.isPending ? (
            <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          ) : (
            <LogOut className="size-4" aria-hidden="true" />
          )}
          Logout
        </Button>
      </div>
    </div>
  );
}
