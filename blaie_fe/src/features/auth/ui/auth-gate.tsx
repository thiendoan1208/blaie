"use client";

import type { ReactNode } from "react";
import { useEffect } from "react";
import { useUser } from "../model/user-context";
import { routePaths } from "@/shared/routes/route-paths";

export function AuthGate({ children }: { children: ReactNode }) {
  const { user, isError, isPending } = useUser();

  useEffect(() => {
    if (!isError) {
      return;
    }

    const loginUrl = new URL(routePaths.login, window.location.origin);
    loginUrl.searchParams.set("next", `${window.location.pathname}${window.location.search}`);
    window.location.assign(loginUrl.toString());
  }, [isError]);

  useEffect(() => {
    if (!user || user.emailVerified) {
      return;
    }

    window.location.assign(routePaths.verifyEmail);
  }, [user]);

  if (isPending || isError || (user && !user.emailVerified)) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background px-6 text-sm text-muted-foreground">
        Loading...
      </div>
    );
  }

  return children;
}
