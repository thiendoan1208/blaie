"use client";

import type { ReactNode } from "react";
import { useEffect } from "react";
import { routePaths } from "@/shared/routes/route-paths";
import { useCurrentUserQuery } from "../model/auth.queries";

export function AuthGate({ children }: { children: ReactNode }) {
  const { isError, isPending } = useCurrentUserQuery();

  useEffect(() => {
    if (!isError) {
      return;
    }

    const loginUrl = new URL(routePaths.login, window.location.origin);
    loginUrl.searchParams.set("next", `${window.location.pathname}${window.location.search}`);
    window.location.assign(loginUrl.toString());
  }, [isError]);

  if (isPending || isError) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background px-6 text-sm text-muted-foreground">
        Loading...
      </div>
    );
  }

  return children;
}
