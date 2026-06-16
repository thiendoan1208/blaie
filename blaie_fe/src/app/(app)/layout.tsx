import Link from "next/link";
import type { ReactNode } from "react";

const navItems = [
  { href: "/inbox", label: "Inbox" },
  { href: "/tasks", label: "Tasks" },
  { href: "/notes", label: "Notes" },
  { href: "/calendar", label: "Calendar" },
  { href: "/reminders", label: "Reminders" },
  { href: "/information", label: "Information" },
  { href: "/search", label: "Search" },
  { href: "/admin", label: "Admin" },
];

export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen">
      <header className="border-b border-zinc-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4">
          <Link className="text-sm font-semibold" href="/">
            Blaie
          </Link>
          <nav className="flex flex-wrap gap-3 text-sm text-zinc-600">
            {navItems.map((item) => (
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
