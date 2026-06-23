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
          className="group flex select-none items-center gap-2.5 text-[#141413] no-underline"
        >
          <LandingBrandMark />
        </Link>

        <nav className="hidden items-center gap-2 text-[15px] font-medium text-charcoal md:flex ">
          {navItems.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="rounded-[9.6px] px-3 py-1.5 transition-colors duration-200 hover:bg-warm-coal hover:text-[#141413] no-underline"
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-3">
          <Link
            href={routePaths.login}
            className="inline-flex h-10 items-center gap-2 rounded-[9.6px] border border-[#d2cdc0] bg-[#faf8f0] px-4 text-[15px] font-medium text-charcoal shadow-[0_1px_0_rgba(20,20,19,0.04)] transition-all duration-200 hover:border-[#bfb8a9] hover:bg-white hover:shadow-[0_8px_20px_rgba(20,20,19,0.08)] no-underline"
          >
            Try Blaie
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </div>
    </header>
  );
}
