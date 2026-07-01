"use client";

import { Loader2, LogOut, MailCheck, RefreshCw } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useMemo } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { defaultAuthenticatedRoute, routePaths } from "@/shared/routes/route-paths";
import {
  useLogoutMutation,
  useResendEmailVerificationMutation,
} from "../model/auth.mutations";
import { useUser } from "../model/user-context";

export function VerifyEmailPanel() {
  const router = useRouter();
  const { user, isError, isPending } = useUser();
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

  if (isPending || isError || !user || user.emailVerified) {
    return (
      <div className="flex min-h-48 items-center justify-center text-sm text-muted-foreground">
        Loading...
      </div>
    );
  }

  return (
    <div className="space-y-7">
      <div className="space-y-3">
        <div className="flex size-11 items-center justify-center rounded-lg border border-brand-accent/25 bg-brand-accent/10 text-brand-accent">
          <MailCheck className="size-5" aria-hidden="true" />
        </div>
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase text-muted-foreground">
            Verify email
          </p>
          <h1 className="text-3xl font-semibold tracking-normal text-foreground">
            Check your email
          </h1>
          <p className="text-sm leading-6 text-muted-foreground">
            {statusText}
          </p>
        </div>
      </div>

      <div className="rounded-lg border border-border bg-muted/60 px-4 py-3 text-sm text-foreground">
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
          className="h-11 border-border bg-transparent text-foreground hover:bg-muted"
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
