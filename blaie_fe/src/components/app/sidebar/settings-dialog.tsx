"use client";

import { type Dispatch, type FormEvent, type SetStateAction, useState } from "react";
import { useRouter } from "next/navigation";
import { useTheme } from "next-themes";
import { KeyRound, Palette, UserRound, X } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  useLogoutMutation,
  useUpdatePasswordMutation,
  useUpdateUsernameMutation,
} from "@/features/auth/model/auth.mutations";
import {
  updatePasswordSchema,
  updateUsernameSchema,
} from "@/features/auth/model/auth.schema";
import type { AuthUser } from "@/features/auth/types/types";
import { isAppError } from "@/shared/api/errors/app-error";
import { routePaths } from "@/shared/routes/route-paths";
import { cn } from "@/lib/utils";

import { AccountSettingsPanel } from "./account-settings-panel";
import { PersonalizationSettingsPanel } from "./personalization-settings-panel";
import { SecuritySettingsPanel } from "./security-settings-panel";
import type { SettingsTab, ThemeChoice } from "./types";
import { toThemeChoice } from "./utils";

type SettingsDialogProps = {
  activeTab: SettingsTab;
  open: boolean;
  user: AuthUser;
  onActiveTabChange: Dispatch<SetStateAction<SettingsTab>>;
  onOpenChange: (open: boolean) => void;
};

export function SettingsDialog({
  activeTab,
  open,
  user,
  onActiveTabChange,
  onOpenChange,
}: SettingsDialogProps) {
  const router = useRouter();
  const { theme, setTheme } = useTheme();
  const [selectedTheme, setSelectedTheme] = useState<ThemeChoice | null>(null);
  const [usernameValue, setUsernameValue] = useState("");
  const [usernameError, setUsernameError] = useState<string | null>(null);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordErrors, setPasswordErrors] = useState<Record<string, string>>(
    {},
  );
  const updateUsernameMutation = useUpdateUsernameMutation();
  const updatePasswordMutation = useUpdatePasswordMutation();
  const logoutMutation = useLogoutMutation();
  const isPasswordSubmitPending =
    updatePasswordMutation.isPending || logoutMutation.isPending;
  const activeTheme = toThemeChoice(theme);
  const selectedThemeValue = selectedTheme ?? activeTheme;

  const resetFormState = () => {
    setUsernameError(null);
    setCurrentPassword("");
    setNewPassword("");
    setConfirmPassword("");
    setPasswordErrors({});
  };

  const handleOpenChange = (nextOpen: boolean) => {
    if (nextOpen) {
      setSelectedTheme(toThemeChoice(theme));
    } else {
      resetFormState();
    }
    onOpenChange(nextOpen);
  };

  const handleUsernameSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setUsernameError(null);

    const parsed = updateUsernameSchema.safeParse({ username: usernameValue });
    if (!parsed.success) {
      setUsernameError(
        parsed.error.flatten().fieldErrors.username?.[0] ??
          "Enter a valid username.",
      );
      return;
    }

    try {
      await updateUsernameMutation.mutateAsync({
        username: parsed.data.username,
      });
      toast.success(user.username ? "Username updated" : "Username created");
    } catch (error) {
      if (isAppError(error)) {
        setUsernameError(
          error.fieldErrors?.username?.[0] ??
            (error.code === "USERNAME_ALREADY_EXISTS"
              ? "Username already exists."
              : error.message),
        );
        return;
      }
      setUsernameError("Unable to update username. Please try again.");
    }
  };

  const handlePasswordSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setPasswordErrors({});

    const parsed = updatePasswordSchema.safeParse({
      currentPassword,
      newPassword,
      confirmPassword,
    });
    if (!parsed.success) {
      const fieldErrors = parsed.error.flatten().fieldErrors;
      setPasswordErrors({
        currentPassword: fieldErrors.currentPassword?.[0] ?? "",
        newPassword: fieldErrors.newPassword?.[0] ?? "",
        confirmPassword: fieldErrors.confirmPassword?.[0] ?? "",
      });
      return;
    }

    if (user.hasPassword && !parsed.data.currentPassword?.trim()) {
      setPasswordErrors({ currentPassword: "Current password is required." });
      return;
    }

    try {
      await updatePasswordMutation.mutateAsync({
        currentPassword: user.hasPassword
          ? parsed.data.currentPassword
          : undefined,
        newPassword: parsed.data.newPassword,
      });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      onOpenChange(false);
      try {
        await logoutMutation.mutateAsync();
      } catch {
        toast.error(
          "Password changed, but we could not clear this session automatically.",
        );
      }
      toast.success(
        user.hasPassword
          ? "Password updated. Please sign in again."
          : "Password set. Please sign in again.",
      );
      router.replace(routePaths.login);
      router.refresh();
    } catch (error) {
      if (isAppError(error)) {
        setPasswordErrors({
          currentPassword: error.fieldErrors?.currentPassword?.[0] ?? "",
          newPassword: error.fieldErrors?.newPassword?.[0] ?? "",
          root: error.message,
        });
        return;
      }
      setPasswordErrors({
        root: "Unable to update password. Please try again.",
      });
    }
  };

  const handleThemeSave = () => {
    setTheme(selectedThemeValue);
    toast.success("Appearance saved");
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent
        showCloseButton={false}
        className="max-w-3xl overflow-hidden border border-border bg-card p-0 text-card-foreground sm:max-w-[760px]"
      >
        <DialogClose asChild>
          <Button
            variant="ghost"
            size="icon-sm"
            className="absolute top-4 right-4 z-40 cursor-pointer text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            <X className="size-4" />
            <span className="sr-only">Close</span>
          </Button>
        </DialogClose>

        <div className="flex h-[500px]">
          <div className="w-[200px] shrink-0 border-r border-border bg-muted/20 p-5 flex flex-col gap-1.5">
            <div className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground px-3 mb-2 mt-1 font-label-sm">
              Settings
            </div>
            <SettingsTabButton
              activeTab={activeTab}
              icon={UserRound}
              label="Account"
              tab="account"
              onActiveTabChange={onActiveTabChange}
            />
            <SettingsTabButton
              activeTab={activeTab}
              icon={Palette}
              label="Personalization"
              tab="personalization"
              onActiveTabChange={onActiveTabChange}
            />
            <SettingsTabButton
              activeTab={activeTab}
              icon={KeyRound}
              label="Security"
              tab="security"
              onActiveTabChange={onActiveTabChange}
            />
          </div>

          <div className="flex-1 overflow-y-auto p-8 flex flex-col">
            <DialogHeader className="mb-6">
              <DialogTitle className="font-display-sm text-lg font-semibold tracking-tight text-foreground">
                {activeTab === "account"
                  ? "Account settings"
                  : activeTab === "personalization"
                    ? "Personalization"
                    : "Security settings"}
              </DialogTitle>
              <DialogDescription className="text-xs text-muted-foreground">
                {activeTab === "account"
                  ? "Update your username and sign-in preferences."
                  : activeTab === "personalization"
                    ? "Choose how Blaie appears."
                    : "Update your password used for local sign-in."}
              </DialogDescription>
            </DialogHeader>

            <div className="flex-1 flex flex-col">
              {activeTab === "account" && (
                <AccountSettingsPanel
                  isPending={updateUsernameMutation.isPending}
                  user={user}
                  usernameError={usernameError}
                  usernameValue={usernameValue}
                  onSubmit={handleUsernameSubmit}
                  onUsernameChange={(value) => {
                    setUsernameValue(value);
                    setUsernameError(null);
                  }}
                />
              )}

              {activeTab === "personalization" && (
                <PersonalizationSettingsPanel
                  selectedTheme={selectedThemeValue}
                  onSave={handleThemeSave}
                  onSelectTheme={setSelectedTheme}
                />
              )}

              {activeTab === "security" && (
                <SecuritySettingsPanel
                  confirmPassword={confirmPassword}
                  currentPassword={currentPassword}
                  isPending={isPasswordSubmitPending}
                  newPassword={newPassword}
                  passwordErrors={passwordErrors}
                  user={user}
                  onConfirmPasswordChange={(value) => {
                    setConfirmPassword(value);
                    setPasswordErrors({});
                  }}
                  onCurrentPasswordChange={(value) => {
                    setCurrentPassword(value);
                    setPasswordErrors({});
                  }}
                  onNewPasswordChange={(value) => {
                    setNewPassword(value);
                    setPasswordErrors({});
                  }}
                  onSubmit={handlePasswordSubmit}
                />
              )}
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}

type SettingsTabButtonProps = {
  activeTab: SettingsTab;
  icon: typeof UserRound;
  label: string;
  tab: SettingsTab;
  onActiveTabChange: Dispatch<SetStateAction<SettingsTab>>;
};

function SettingsTabButton({
  activeTab,
  icon: Icon,
  label,
  tab,
  onActiveTabChange,
}: SettingsTabButtonProps) {
  return (
    <button
      onClick={() => onActiveTabChange(tab)}
      className={cn(
        "flex items-center gap-2.5 w-full text-left px-3 py-2 text-xs font-semibold rounded-md transition-colors cursor-pointer",
        activeTab === tab
          ? "bg-accent text-accent-foreground"
          : "text-muted-foreground hover:bg-muted hover:text-foreground",
      )}
    >
      <Icon className="size-3.5 shrink-0" />
      {label}
    </button>
  );
}
