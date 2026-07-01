"use client";

import { useRouter } from "next/navigation";
import {
  ChevronUp,
  CircleHelp,
  LogOut,
  Palette,
  Settings,
  UserRound,
} from "lucide-react";
import { toast } from "sonner";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuSkeleton,
} from "@/components/ui/sidebar";
import { useLogoutMutation } from "@/features/auth/model/auth.mutations";
import type { AuthUser } from "@/features/auth/types/types";
import { routePaths } from "@/shared/routes/route-paths";

import type { SettingsTab } from "./types";
import { getInitials } from "./utils";

type AccountMenuProps = {
  displayName: string;
  isPending: boolean;
  user: AuthUser | undefined;
  onOpenPersonalDetails: () => void;
  onOpenSettings: (tab: SettingsTab) => void;
};

export function AccountMenu({
  displayName,
  isPending,
  user,
  onOpenPersonalDetails,
  onOpenSettings,
}: AccountMenuProps) {
  const router = useRouter();
  const logoutMutation = useLogoutMutation();

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

  return (
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
                <span className="flex size-8 shrink-0 select-none items-center justify-center rounded-full border border-sidebar-border/60 bg-muted text-xs font-semibold text-muted-foreground shadow-xs">
                  {getInitials(displayName)}
                </span>
                <span className="flex min-w-0 flex-1 flex-col overflow-hidden transition-[width,opacity] duration-200 group-data-[collapsible=icon]:hidden">
                  <span className="truncate text-xs font-semibold text-sidebar-foreground">
                    {displayName}
                  </span>
                  <span className="truncate text-xs text-muted-foreground">
                    Free
                  </span>
                </span>
                <ChevronUp className="size-4 shrink-0 text-muted-foreground transition-transform duration-200 group-data-[collapsible=icon]:hidden" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              side="top"
              align="start"
              sideOffset={8}
              className="w-64 border border-border bg-popover p-2 text-popover-foreground shadow-xl shadow-foreground/10"
            >
              <div className="flex items-center gap-3 rounded-md px-2 py-2.5">
                <div className="flex size-9 shrink-0 select-none items-center justify-center rounded-full border border-border bg-muted text-xs font-semibold text-muted-foreground">
                  {getInitials(displayName)}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-semibold">{displayName}</p>
                  <p className="truncate text-xs text-muted-foreground">Free</p>
                </div>
              </div>

              <DropdownMenuSeparator className="my-1 bg-border" />

              <DropdownMenuGroup>
                <DropdownMenuItem
                  onSelect={onOpenPersonalDetails}
                  className="h-9 gap-3 text-popover-foreground focus:bg-accent focus:text-accent-foreground"
                >
                  <UserRound className="size-4" />
                  <span>Personal details</span>
                </DropdownMenuItem>

                <DropdownMenuItem
                  onSelect={() => onOpenSettings("personalization")}
                  className="h-9 gap-3 text-popover-foreground focus:bg-accent focus:text-accent-foreground"
                >
                  <Palette className="size-4" />
                  <span>Personalization</span>
                </DropdownMenuItem>
                <DropdownMenuItem
                  onSelect={() => onOpenSettings("account")}
                  className="h-9 gap-3 text-popover-foreground focus:bg-accent focus:text-accent-foreground"
                >
                  <Settings className="size-4" />
                  <span>Settings</span>
                </DropdownMenuItem>
              </DropdownMenuGroup>

              <DropdownMenuGroup>
                <DropdownMenuItem className="h-9 gap-3 text-popover-foreground focus:bg-accent focus:text-accent-foreground">
                  <CircleHelp className="size-4" />
                  <span>Help</span>
                </DropdownMenuItem>
              </DropdownMenuGroup>

              <DropdownMenuSeparator className="my-1 bg-border" />

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
  );
}
