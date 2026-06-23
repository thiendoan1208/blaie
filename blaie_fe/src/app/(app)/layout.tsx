import Link from "next/link";
import type { ReactNode } from "react";
import { appNavigationItems, routePaths } from "@/shared/routes/route-paths";

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen">
      <header className="border-b border-zinc-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4">
          <Link className="text-sm font-semibold" href={routePaths.home}>
            Blaie
          </Link>
          <nav className="flex flex-wrap gap-3 text-sm text-zinc-600">
            {appNavigationItems.map((item) => (
              <Link key={item.href} href={item.href}>
                {item.label}
              </Link>
            ))}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-7xl p-4">{children}</main>
    </div>
  );
}
