import type { ReactNode } from "react";

import {
  SidebarInset,
  SidebarProvider,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import { AppSidebar } from "@/components/app/app-sidebar";

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <div className="flex min-h-screen flex-col bg-obsidian-canvas">
          <header className="sticky top-0 z-30 flex h-14 shrink-0 items-center gap-2 px-4">
            <SidebarTrigger className="h-8 w-8 cursor-pointer text-stone-gray transition-colors hover:text-ivory-text" />
            <span className="ml-auto font-display-sm text-[22px] font-normal tracking-tight">
              Blaie
            </span>
          </header>
          <main className="flex-1 p-4 md:p-6">{children}</main>
        </div>
      </SidebarInset>
    </SidebarProvider>
  );
}
