"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

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
  SidebarRail,
} from "@/components/ui/sidebar";
import { useUser } from "@/features/auth/model/user-context";
import { appNavigationItems } from "@/shared/routes/route-paths";

import { AccountMenu } from "./account-menu";
import { PersonalDetailsDialog } from "./personal-details-dialog";
import { SettingsDialog } from "./settings-dialog";
import type { AccountDialog, SettingsTab } from "./types";

export function AppSidebar() {
  const pathname = usePathname();
  const { user, isPending } = useUser();
  const [accountDialog, setAccountDialog] = useState<AccountDialog>(null);
  const [activeTab, setActiveTab] = useState<SettingsTab>("account");

  const isActive = (href: string) => {
    return pathname === href || pathname.startsWith(`${href}/`);
  };

  const openSettings = (tab: SettingsTab = "account") => {
    setActiveTab(tab);
    setAccountDialog("settings");
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
        <AccountMenu
          displayName={displayName}
          isPending={isPending}
          user={user}
          onOpenPersonalDetails={() => setAccountDialog("personal-details")}
          onOpenSettings={openSettings}
        />
      </SidebarFooter>
      <SidebarRail />

      {user ? (
        <>
          <PersonalDetailsDialog
            displayName={displayName}
            formattedCreatedAt={formattedCreatedAt}
            open={accountDialog === "personal-details"}
            user={user}
            onOpenChange={(open) =>
              setAccountDialog(open ? "personal-details" : null)
            }
          />
          <SettingsDialog
            activeTab={activeTab}
            open={accountDialog === "settings"}
            user={user}
            onActiveTabChange={setActiveTab}
            onOpenChange={(open) => {
              if (!open) {
                setAccountDialog(null);
              }
            }}
          />
        </>
      ) : null}
    </Sidebar>
  );
}
