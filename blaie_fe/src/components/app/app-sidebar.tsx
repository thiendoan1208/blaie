"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import Image from "next/image";
import { LogOut } from "lucide-react";
import { toast } from "sonner";

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
  useSidebar,
} from "@/components/ui/sidebar";
import { useLogoutMutation } from "@/features/auth/model/auth.mutations";
import { useUser } from "@/features/auth/model/user-context";
import { appNavigationItems, routePaths } from "@/shared/routes/route-paths";

function getInitials(name?: string) {
  if (!name) {
    return "U";
  }

  return name.trim().charAt(0).toUpperCase();
}

export function AppSidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const { state } = useSidebar();
  const { user, isPending } = useUser();
  const logoutMutation = useLogoutMutation();

  const isActive = (href: string) => {
    return pathname === href || pathname.startsWith(`${href}/`);
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

  const displayName = user?.displayName || user?.username;

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

      <SidebarFooter className="border-t border-sidebar-border/50 p-2 group-data-[collapsible=icon]:p-0">
        <SidebarMenu>
          <SidebarMenuItem>
            {isPending ? (
              <SidebarMenuSkeleton showIcon />
            ) : user ? (
              state === "collapsed" ? (
                <div className="flex h-14 w-full items-center justify-center">
                  <div className="flex size-8 shrink-0 select-none items-center justify-center rounded-full border border-sidebar-border/60 bg-warm-coal text-xs font-semibold text-stone-gray shadow-xs">
                    {getInitials(displayName)}
                  </div>
                </div>
              ) : (
                <div className="flex items-center justify-between gap-2 rounded-lg p-1.5 transition-colors duration-200">
                  <div className="flex items-center gap-3 overflow-hidden">
                    <div className="flex size-8 shrink-0 select-none items-center justify-center rounded-full border border-sidebar-border/60 bg-warm-coal text-xs font-semibold text-stone-gray shadow-xs">
                      {getInitials(displayName)}
                    </div>
                    <div className="flex flex-col overflow-hidden transition-[width,opacity] duration-200 group-data-[collapsible=icon]:w-0 group-data-[collapsible=icon]:opacity-0">
                      <span className="truncate text-xs font-semibold text-ivory-text">
                        {displayName}
                      </span>
                      <span className="truncate text-xs text-gray-400">
                        Free
                      </span>
                    </div>
                  </div>

                  <button
                    onClick={handleLogout}
                    disabled={logoutMutation.isPending}
                    title="Log out"
                    className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md text-stone-gray transition-colors duration-200 hover:bg-warm-coal hover:text-ivory-text active:scale-95 disabled:pointer-events-none disabled:opacity-50 group-data-[collapsible=icon]:hidden"
                  >
                    <LogOut className="size-4" />
                  </button>
                </div>
              )
            ) : null}
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
