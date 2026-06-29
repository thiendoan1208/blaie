"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import Image from "next/image";
import { type FormEvent, useState } from "react";
import { cn } from "@/lib/utils";
import {
  ChevronUp,
  CircleHelp,
  KeyRound,
  LogOut,
  Palette,
  Settings,
  UserRound,
  X,
} from "lucide-react";
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
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSkeleton,
  SidebarRail,
} from "@/components/ui/sidebar";
import {
  useLogoutMutation,
  useUpdatePasswordMutation,
  useUpdateUsernameMutation,
} from "@/features/auth/model/auth.mutations";
import {
  updatePasswordSchema,
  updateUsernameSchema,
} from "@/features/auth/model/auth.schema";
import { useUser } from "@/features/auth/model/user-context";
import { isAppError } from "@/shared/api/errors/app-error";
import { appNavigationItems, routePaths } from "@/shared/routes/route-paths";

type AccountDialog = "personal-details" | "settings" | null;

function getInitials(name?: string) {
  if (!name) {
    return "U";
  }

  return name.trim().charAt(0).toUpperCase();
}

export function AppSidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { user, isPending } = useUser();
  const [accountDialog, setAccountDialog] = useState<AccountDialog>(null);
  const [activeTab, setActiveTab] = useState<"account" | "security">("account");
  const [usernameValue, setUsernameValue] = useState("");
  const [usernameError, setUsernameError] = useState<string | null>(null);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordErrors, setPasswordErrors] = useState<Record<string, string>>(
    {},
  );
  const logoutMutation = useLogoutMutation();
  const updateUsernameMutation = useUpdateUsernameMutation();
  const updatePasswordMutation = useUpdatePasswordMutation();
  const isPasswordSubmitPending =
    updatePasswordMutation.isPending || logoutMutation.isPending;

  const isActive = (href: string) => {
    return pathname === href || pathname.startsWith(`${href}/`);
  };

  const openSettings = () => {
    setActiveTab("account");
    setUsernameError(null);
    setCurrentPassword("");
    setNewPassword("");
    setConfirmPassword("");
    setPasswordErrors({});
    setAccountDialog("settings");
  };

  const handleLogout = async () => {
    try {
      await logoutMutation.mutateAsync();
      toast.success("Successfully logged out");
    } catch {
      toast.error("Failed to log out. Please try again.");
    } finally {
      router.replace(routePaths.login);
      router.refresh();
    }
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
      toast.success(user?.username ? "Username updated" : "Username created");
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

    if (user?.hasPassword && !parsed.data.currentPassword?.trim()) {
      setPasswordErrors({ currentPassword: "Current password is required." });
      return;
    }

    try {
      await updatePasswordMutation.mutateAsync({
        currentPassword: user?.hasPassword
          ? parsed.data.currentPassword
          : undefined,
        newPassword: parsed.data.newPassword,
      });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setAccountDialog(null);
      try {
        await logoutMutation.mutateAsync();
      } catch {
        toast.error(
          "Password changed, but we could not clear this session automatically.",
        );
      }
      toast.success(
        user?.hasPassword
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

  const displayName = user?.displayName || user?.username || "Blaie User";
  const formattedCreatedAt = user?.createdAt
    ? new Intl.DateTimeFormat("en", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(user.createdAt))
    : "Unknown";

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader className="p-0">
        <div className="flex h-14 items-center justify-center">
          <Image
            src="/logo.svg"
            alt=""
            aria-hidden="true"
            width={28}
            height={28}
          />
        </div>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel className="group-data-[collapsible=icon]:opacity-0 transition-opacity duration-200">
            Workspace
          </SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu className="gap-1.5">
              {appNavigationItems.map((item) => (
                <SidebarMenuItem key={item.label}>
                  <SidebarMenuButton
                    asChild
                    isActive={isActive(item.href)}
                    tooltip={item.label}
                  >
                    <Link
                      href={item.href}
                      className="group/btn flex items-center gap-3 w-full transition-transform duration-200 active:scale-[0.98]"
                    >
                      <item.icon className="size-4 shrink-0 transition-transform duration-200 group-hover/btn:scale-110" />
                      <span className="font-medium">{item.label}</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter className="border-t border-sidebar-border/50 p-2 group-data-[collapsible=icon]:h-14 group-data-[collapsible=icon]:items-center group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:p-0">
        <SidebarMenu className="group-data-[collapsible=icon]:h-full group-data-[collapsible=icon]:items-center group-data-[collapsible=icon]:justify-center">
          <SidebarMenuItem className="group-data-[collapsible=icon]:flex group-data-[collapsible=icon]:h-full group-data-[collapsible=icon]:w-full group-data-[collapsible=icon]:items-center group-data-[collapsible=icon]:justify-center">
            {isPending ? (
              <SidebarMenuSkeleton showIcon />
            ) : user ? (
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button
                    type="button"
                    className="flex min-h-12 w-full cursor-pointer items-center gap-3 rounded-lg p-1.5 text-left outline-hidden transition-colors duration-200 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground focus-visible:ring-2 focus-visible:ring-sidebar-ring group-data-[collapsible=icon]:mx-auto group-data-[collapsible=icon]:size-10 group-data-[collapsible=icon]:min-h-10 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:gap-0 group-data-[collapsible=icon]:rounded-full group-data-[collapsible=icon]:p-0"
                  >
                    <span className="flex size-8 shrink-0 select-none items-center justify-center rounded-full border border-sidebar-border/60 bg-warm-coal text-xs font-semibold text-stone-gray shadow-xs">
                      {getInitials(displayName)}
                    </span>
                    <span className="flex min-w-0 flex-1 flex-col overflow-hidden transition-[width,opacity] duration-200 group-data-[collapsible=icon]:hidden">
                      <span className="truncate text-xs font-semibold text-ivory-text">
                        {displayName}
                      </span>
                      <span className="truncate text-xs text-stone-gray">
                        Free
                      </span>
                    </span>
                    <ChevronUp className="size-4 shrink-0 text-stone-gray transition-transform duration-200 group-data-[collapsible=icon]:hidden" />
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent
                  side="top"
                  align="start"
                  sideOffset={8}
                  className="w-64 border border-graphite-border bg-charcoal-surface p-2 text-ivory-text shadow-xl shadow-graphite-border/40"
                >
                  <div className="flex items-center gap-3 rounded-md px-2 py-2.5">
                    <div className="flex size-9 shrink-0 select-none items-center justify-center rounded-full border border-graphite-border bg-warm-coal text-xs font-semibold text-warm-slate">
                      {getInitials(displayName)}
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold">
                        {displayName}
                      </p>
                      <p className="truncate text-xs text-stone-gray">Free</p>
                    </div>
                  </div>

                  <DropdownMenuSeparator className="my-1 bg-graphite-border" />

                  <DropdownMenuGroup>
                    <DropdownMenuItem
                      onSelect={() => setAccountDialog("personal-details")}
                      className="h-9 gap-3 text-ivory-text focus:bg-warm-coal focus:text-ivory-text"
                    >
                      <UserRound className="size-4" />
                      <span>Personal details</span>
                    </DropdownMenuItem>

                    <DropdownMenuItem className="h-9 gap-3 text-ivory-text focus:bg-warm-coal focus:text-ivory-text">
                      <Palette className="size-4" />
                      <span>Personalization</span>
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onSelect={openSettings}
                      className="h-9 gap-3 text-ivory-text focus:bg-warm-coal focus:text-ivory-text"
                    >
                      <Settings className="size-4" />
                      <span>Settings</span>
                    </DropdownMenuItem>
                  </DropdownMenuGroup>

                  <DropdownMenuGroup>
                    <DropdownMenuItem className="h-9 gap-3 text-ivory-text focus:bg-warm-coal focus:text-ivory-text">
                      <CircleHelp className="size-4" />
                      <span>Help</span>
                    </DropdownMenuItem>
                  </DropdownMenuGroup>

                  <DropdownMenuSeparator className="my-1 bg-graphite-border" />

                  <DropdownMenuItem
                    disabled={logoutMutation.isPending}
                    onSelect={handleLogout}
                    variant="destructive"
                    className="h-9 gap-3 focus:bg-destructive/10"
                  >
                    <LogOut className="size-4" />
                    <span>
                      {logoutMutation.isPending ? "Signing out..." : "Sign out"}
                    </span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            ) : null}
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />

      {user ? (
        <>
          <Dialog
            open={accountDialog === "personal-details"}
            onOpenChange={(open) =>
              setAccountDialog(open ? "personal-details" : null)
            }
          >
            <DialogContent
              showCloseButton={false}
              className="max-w-lg overflow-hidden border border-graphite-border bg-charcoal-surface p-0 text-ivory-text"
            >
              <DialogClose asChild>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="absolute top-4 right-4 text-stone-gray hover:bg-warm-coal hover:text-ivory-text"
                >
                  <X className="size-4" />
                  <span className="sr-only">Close</span>
                </Button>
              </DialogClose>
              <div className="p-5">
                <DialogHeader>
                  <DialogTitle>Personal details</DialogTitle>
                  <DialogDescription>
                    Review the profile information currently attached to your
                    account.
                  </DialogDescription>
                </DialogHeader>

                <div className="mt-5 flex items-center gap-4 rounded-lg border border-graphite-border bg-obsidian-canvas p-4">
                  <div className="flex size-12 shrink-0 select-none items-center justify-center rounded-full border border-graphite-border bg-warm-coal text-sm font-semibold text-warm-slate">
                    {getInitials(displayName)}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold">
                      {displayName}
                    </p>
                    <p className="truncate text-xs text-stone-gray">
                      {user.email}
                    </p>
                  </div>
                </div>

                <div className="mt-5 grid gap-3 text-sm">
                  <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
                    <span className="text-stone-gray">Display name</span>
                    <span className="truncate font-medium">
                      {user.displayName || "Not set"}
                    </span>
                  </div>
                  <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
                    <span className="text-stone-gray">Username</span>
                    <span className="truncate font-medium">
                      {user.username || "Not set"}
                    </span>
                  </div>
                  <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
                    <span className="text-stone-gray">Email</span>
                    <span className="truncate font-medium">{user.email}</span>
                  </div>
                  <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
                    <span className="text-stone-gray">Email status</span>
                    <span className="font-medium">
                      {user.emailVerified ? "Verified" : "Not verified"}
                    </span>
                  </div>
                  <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
                    <span className="text-stone-gray">Password</span>
                    <span className="font-medium">
                      {user.hasPassword ? "Set" : "Not set"}
                    </span>
                  </div>
                  <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
                    <span className="text-stone-gray">Joined</span>
                    <span className="truncate font-medium">
                      {formattedCreatedAt}
                    </span>
                  </div>
                  <div className="grid grid-cols-[8rem_minmax(0,1fr)] gap-3">
                    <span className="text-stone-gray">User ID</span>
                    <span className="truncate font-mono text-xs">
                      {user.id}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex justify-end border-t border-graphite-border bg-warm-coal/40 px-5 py-3">
                <DialogClose asChild>
                  <Button variant="outline">Close</Button>
                </DialogClose>
              </div>
            </DialogContent>
          </Dialog>

          <Dialog
            open={accountDialog === "settings"}
            onOpenChange={(open) => {
              if (open) {
                openSettings();
                return;
              }
              setAccountDialog(null);
            }}
          >
            <DialogContent
              showCloseButton={false}
              className="max-w-3xl overflow-hidden border border-graphite-border bg-charcoal-surface p-0 text-ivory-text sm:max-w-[760px]"
            >
              <DialogClose asChild>
                <Button
                  variant="ghost"
                  size="icon-sm"
                  className="absolute top-4 right-4 text-stone-gray hover:bg-warm-coal hover:text-ivory-text z-40 cursor-pointer"
                >
                  <X className="size-4" />
                  <span className="sr-only">Close</span>
                </Button>
              </DialogClose>

              <div className="flex h-[500px]">
                {/* Left Column: Tabs */}
                <div className="w-[200px] shrink-0 border-r border-graphite-border/60 bg-warm-coal/10 p-5 flex flex-col gap-1.5">
                  <div className="text-[10px] font-semibold uppercase tracking-wider text-stone-gray/70 px-3 mb-2 mt-1 font-label-sm">
                    Settings
                  </div>
                  <button
                    onClick={() => setActiveTab("account")}
                    className={cn(
                      "flex items-center gap-2.5 w-full text-left px-3 py-2 text-xs font-semibold rounded-md transition-colors cursor-pointer",
                      activeTab === "account"
                        ? "bg-warm-coal text-ivory-text"
                        : "text-stone-gray hover:bg-warm-coal/40 hover:text-ivory-text",
                    )}
                  >
                    <UserRound className="size-3.5 shrink-0" />
                    Account
                  </button>
                  <button
                    onClick={() => setActiveTab("security")}
                    className={cn(
                      "flex items-center gap-2.5 w-full text-left px-3 py-2 text-xs font-semibold rounded-md transition-colors cursor-pointer",
                      activeTab === "security"
                        ? "bg-warm-coal text-ivory-text"
                        : "text-stone-gray hover:bg-warm-coal/40 hover:text-ivory-text",
                    )}
                  >
                    <KeyRound className="size-3.5 shrink-0" />
                    Security
                  </button>
                </div>

                {/* Right Column: Content Pane */}
                <div className="flex-1 overflow-y-auto p-8 flex flex-col">
                  <DialogHeader className="mb-6">
                    <DialogTitle className="font-display-sm text-lg font-semibold tracking-tight text-ivory-text">
                      {activeTab === "account"
                        ? "Account settings"
                        : "Security settings"}
                    </DialogTitle>
                    <DialogDescription className="text-xs text-stone-gray">
                      {activeTab === "account"
                        ? "Update your username and sign-in preferences."
                        : "Update your password used for local sign-in."}
                    </DialogDescription>
                  </DialogHeader>

                  <div className="flex-1 flex flex-col">
                    {activeTab === "account" && (
                      <form
                        onSubmit={handleUsernameSubmit}
                        className="flex-1 flex flex-col justify-between"
                      >
                        <div className="space-y-5">
                          {user.username ? (
                            <div className="flex flex-col gap-1.5">
                              <span className="text-xs font-semibold text-stone-gray">
                                Current username
                              </span>
                              <div className="flex items-center h-10 px-3 bg-warm-coal/20 border border-graphite-border/30 rounded-md text-sm font-medium text-stone-gray select-all">
                                {user.username}
                              </div>
                            </div>
                          ) : null}

                          <label className="grid gap-1.5 text-xs font-semibold text-stone-gray">
                            {user.username ? "New username" : "Username"}
                            <Input
                              value={usernameValue}
                              onChange={(event) => {
                                setUsernameValue(event.target.value);
                                setUsernameError(null);
                              }}
                              placeholder="your-username"
                              className="h-10 bg-charcoal-surface border-graphite-border text-ivory-text focus-visible:ring-dust-purple"
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
                            disabled={updateUsernameMutation.isPending}
                            className="h-10 px-4 text-xs font-semibold cursor-pointer"
                          >
                            {updateUsernameMutation.isPending
                              ? "Saving..."
                              : user.username
                                ? "Save username"
                                : "Create username"}
                          </Button>
                        </div>
                      </form>
                    )}

                    {activeTab === "security" && (
                      <form
                        onSubmit={handlePasswordSubmit}
                        className="flex-1 flex flex-col justify-between"
                      >
                        <div className="space-y-5">
                          {user.hasPassword ? (
                            <label className="grid gap-1.5 text-xs font-semibold text-stone-gray">
                              Current password
                              <Input
                                type="password"
                                value={currentPassword}
                                onChange={(event) => {
                                  setCurrentPassword(event.target.value);
                                  setPasswordErrors({});
                                }}
                                placeholder="Current password"
                                className="h-10 bg-charcoal-surface border-graphite-border text-ivory-text focus-visible:ring-dust-purple"
                              />
                              {passwordErrors.currentPassword ? (
                                <span className="text-xs font-medium text-destructive">
                                  {passwordErrors.currentPassword}
                                </span>
                              ) : null}
                            </label>
                          ) : null}
                          <label className="grid gap-1.5 text-xs font-semibold text-stone-gray">
                            New password
                            <Input
                              type="password"
                              value={newPassword}
                              onChange={(event) => {
                                setNewPassword(event.target.value);
                                setPasswordErrors({});
                              }}
                              placeholder="New password"
                              className="h-10 bg-charcoal-surface border-graphite-border text-ivory-text focus-visible:ring-dust-purple"
                            />
                            {passwordErrors.newPassword ? (
                              <span className="text-xs font-medium text-destructive">
                                {passwordErrors.newPassword}
                              </span>
                            ) : null}
                          </label>
                          <label className="grid gap-1.5 text-xs font-semibold text-stone-gray">
                            Confirm password
                            <Input
                              type="password"
                              value={confirmPassword}
                              onChange={(event) => {
                                setConfirmPassword(event.target.value);
                                setPasswordErrors({});
                              }}
                              placeholder="Confirm password"
                              className="h-10 bg-charcoal-surface border-graphite-border text-ivory-text focus-visible:ring-dust-purple"
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
                            disabled={isPasswordSubmitPending}
                            className="h-10 px-4 text-xs font-semibold cursor-pointer"
                          >
                            {isPasswordSubmitPending
                              ? "Saving..."
                              : user.hasPassword
                                ? "Save password"
                                : "Set password"}
                          </Button>
                        </div>
                      </form>
                    )}
                  </div>
                </div>
              </div>
            </DialogContent>
          </Dialog>
        </>
      ) : null}
    </Sidebar>
  );
}
