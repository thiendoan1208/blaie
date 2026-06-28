"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { useCurrentUserQuery } from "../model/auth.queries";
import { defaultAuthenticatedRoute, routePaths } from "@/shared/routes/route-paths";

type AuthSessionRedirectProps = {
  redirectPath?: string;
};

export function AuthSessionRedirect({
  redirectPath = defaultAuthenticatedRoute,
}: AuthSessionRedirectProps) {
  const router = useRouter();
  const currentUserQuery = useCurrentUserQuery();

  useEffect(() => {
    if (!currentUserQuery.data) {
      return;
    }

    const nextPath = currentUserQuery.data.emailVerified
      ? safeRedirectPath(redirectPath)
      : routePaths.verifyEmail;

    router.replace(nextPath);
    router.refresh();
  }, [currentUserQuery.data, redirectPath, router]);

  return null;
}

function safeRedirectPath(path: string) {
  return path.startsWith("/") && !path.startsWith("//")
    ? path
    : defaultAuthenticatedRoute;
}
