"use client";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { routePaths } from "@/shared/routes/route-paths";

type GoogleAuthButtonProps = {
  nextPath?: string | null;
  disabled?: boolean;
};

export function GoogleAuthButton({ nextPath, disabled = false }: GoogleAuthButtonProps) {
  return (
    <Button
      asChild
      variant="outline"
      className={cn(
        "h-12 w-full border-border bg-card text-[14px] font-semibold text-foreground transition-[background-color,transform] hover:bg-muted active:scale-[0.99]",
        disabled && "pointer-events-none opacity-50",
      )}
    >
      <a href={googleOAuthStartUrl(nextPath)} aria-disabled={disabled}>
        <span
          className="flex size-5 items-center justify-center rounded-md border border-border bg-background font-anthropic-sans text-[13px] font-semibold"
          aria-hidden="true"
        >
          G
        </span>
        Continue with Google
      </a>
    </Button>
  );
}

export function googleOAuthStartUrl(nextPath?: string | null) {
  const apiBaseUrl =
    process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
  const url = new URL(`${apiBaseUrl.replace(/\/$/, "")}/auth/google/start`);
  url.searchParams.set("next", safeNextPath(nextPath));
  return url.toString();
}

function safeNextPath(nextPath?: string | null) {
  if (!nextPath || !nextPath.startsWith("/") || nextPath.startsWith("//")) {
    return routePaths.inbox;
  }
  return nextPath;
}
