import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { routePaths } from "@/shared/routes/route-paths";
import { LandingBrandMark } from "./LandingBrandMark";

const navItems = [
  { href: "/features", label: "Features" },
  { href: "/docs", label: "Docs" },
  { href: "/pricing", label: "Pricing" },
  { href: "/about", label: "About" },
];

export function LandingNavigation() {
  return (
    <header className="fixed left-0 right-0 top-0 z-50 bg-transparent">
      <div className="mx-auto flex h-20 max-w-300 items-center justify-between px-6">
        <Link
          href={routePaths.home}
          className="group flex select-none items-center gap-2.5 text-foreground no-underline"
        >
          <LandingBrandMark />
        </Link>

        <nav className="hidden items-center gap-2 text-[15px] font-medium text-foreground md:flex ">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="rounded-[9.6px] px-3 py-1.5 transition-colors duration-200 hover:bg-muted hover:text-foreground no-underline"
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-3">
          <Link
            href={routePaths.login}
            className="inline-flex h-10 items-center gap-2 rounded-[9.6px] border border-border bg-card px-4 text-[15px] font-medium text-foreground shadow-[0_1px_0_var(--soft-shadow)] transition-all duration-200 hover:border-ring hover:bg-background hover:shadow-[0_8px_20px_var(--soft-shadow)] no-underline"
          >
            Try Blaie
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </div>
    </header>
  );
}
