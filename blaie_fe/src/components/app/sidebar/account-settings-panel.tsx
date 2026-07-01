"use client";

import type { FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { AuthUser } from "@/features/auth/types/types";

type AccountSettingsPanelProps = {
  isPending: boolean;
  user: AuthUser;
  usernameError: string | null;
  usernameValue: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUsernameChange: (value: string) => void;
};

export function AccountSettingsPanel({
  isPending,
  user,
  usernameError,
  usernameValue,
  onSubmit,
  onUsernameChange,
}: AccountSettingsPanelProps) {
  return (
    <form onSubmit={onSubmit} className="flex-1 flex flex-col justify-between">
      <div className="space-y-5">
        {user.username ? (
          <div className="flex flex-col gap-1.5">
            <span className="text-xs font-semibold text-muted-foreground">
              Current username
            </span>
            <div className="flex items-center h-10 px-3 bg-muted/40 border border-border rounded-md text-sm font-medium text-muted-foreground select-all">
              {user.username}
            </div>
          </div>
        ) : null}

        <label className="grid gap-1.5 text-xs font-semibold text-muted-foreground">
          {user.username ? "New username" : "Username"}
          <Input
            value={usernameValue}
            onChange={(event) => onUsernameChange(event.target.value)}
            placeholder="your-username"
            className="h-10 border-border bg-card text-foreground focus-visible:ring-ring"
          />
        </label>
        {usernameError ? (
          <p className="text-xs font-medium text-destructive">
            {usernameError}
          </p>
        ) : null}
      </div>

      <div className="mt-8 flex justify-end">
        <Button
          type="submit"
          disabled={isPending}
          className="h-10 px-4 text-xs font-semibold cursor-pointer"
        >
          {isPending ? "Saving..." : user.username ? "Save username" : "Create username"}
        </Button>
      </div>
    </form>
  );
}
