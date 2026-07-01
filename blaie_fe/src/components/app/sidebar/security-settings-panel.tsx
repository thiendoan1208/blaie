"use client";

import type { FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { AuthUser } from "@/features/auth/types/types";

type SecuritySettingsPanelProps = {
  confirmPassword: string;
  currentPassword: string;
  isPending: boolean;
  newPassword: string;
  passwordErrors: Record<string, string>;
  user: AuthUser;
  onConfirmPasswordChange: (value: string) => void;
  onCurrentPasswordChange: (value: string) => void;
  onNewPasswordChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export function SecuritySettingsPanel({
  confirmPassword,
  currentPassword,
  isPending,
  newPassword,
  passwordErrors,
  user,
  onConfirmPasswordChange,
  onCurrentPasswordChange,
  onNewPasswordChange,
  onSubmit,
}: SecuritySettingsPanelProps) {
  return (
    <form onSubmit={onSubmit} className="flex-1 flex flex-col justify-between">
      <div className="space-y-5">
        {user.hasPassword ? (
          <label className="grid gap-1.5 text-xs font-semibold text-muted-foreground">
            Current password
            <Input
              type="password"
              value={currentPassword}
              onChange={(event) => onCurrentPasswordChange(event.target.value)}
              placeholder="Current password"
              className="h-10 border-border bg-card text-foreground focus-visible:ring-ring"
            />
            {passwordErrors.currentPassword ? (
              <span className="text-xs font-medium text-destructive">
                {passwordErrors.currentPassword}
              </span>
            ) : null}
          </label>
        ) : null}
        <label className="grid gap-1.5 text-xs font-semibold text-muted-foreground">
          New password
          <Input
            type="password"
            value={newPassword}
            onChange={(event) => onNewPasswordChange(event.target.value)}
            placeholder="New password"
            className="h-10 border-border bg-card text-foreground focus-visible:ring-ring"
          />
          {passwordErrors.newPassword ? (
            <span className="text-xs font-medium text-destructive">
              {passwordErrors.newPassword}
            </span>
          ) : null}
        </label>
        <label className="grid gap-1.5 text-xs font-semibold text-muted-foreground">
          Confirm password
          <Input
            type="password"
            value={confirmPassword}
            onChange={(event) => onConfirmPasswordChange(event.target.value)}
            placeholder="Confirm password"
            className="h-10 border-border bg-card text-foreground focus-visible:ring-ring"
          />
          {passwordErrors.confirmPassword ? (
            <span className="text-xs font-medium text-destructive">
              {passwordErrors.confirmPassword}
            </span>
          ) : null}
        </label>
        {passwordErrors.root ? (
          <p className="text-xs font-medium text-destructive">
            {passwordErrors.root}
          </p>
        ) : null}
      </div>

      <div className="mt-8 flex justify-end">
        <Button
          type="submit"
          disabled={isPending}
          className="h-10 px-4 text-xs font-semibold cursor-pointer"
        >
          {isPending ? "Saving..." : user.hasPassword ? "Save password" : "Set password"}
        </Button>
      </div>
    </form>
  );
}
