import type { ReactNode } from "react";
import Link from "next/link";
import { AuthBrandMark, AuthBrandPanel } from "./auth-brand";
import { routePaths } from "@/shared/routes/route-paths";

type AuthShellProps = {
  children: ReactNode;
  footer: ReactNode;
};

export function AuthShell({ children, footer }: AuthShellProps) {
  return (
    <div className="grid min-h-[100dvh] bg-obsidian-canvas text-ivory-text lg:grid-cols-[minmax(0,1.08fr)_minmax(430px,0.92fr)]">
      <AuthBrandPanel />

      <section className="relative flex min-h-[100dvh] flex-col overflow-hidden bg-obsidian-canvas px-5 sm:px-8 lg:px-10">
        <div
          className="pointer-events-none absolute right-[-12rem] top-[-12rem] size-[26rem] rounded-full bg-dust-purple/5 blur-[120px]"
          aria-hidden="true"
        />

        <header className="relative flex h-20 items-center justify-between lg:justify-end">
          <div className="lg:hidden">
            <AuthBrandMark compact />
          </div>
          <Link
            href={routePaths.home}
            className="text-xs font-medium tracking-wide text-stone-gray transition-colors hover:text-ivory-text focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-dust-purple"
          >
            Back home
          </Link>
        </header>

        <main className="relative flex flex-1 items-center justify-center py-10 sm:py-14">
          <div className="w-full max-w-[28rem]">{children}</div>
        </main>

        <footer className="relative flex min-h-20 items-center justify-center border-t border-graphite-border py-5 text-center text-sm text-stone-gray">
          {footer}
        </footer>
      </section>
    </div>
  );
}
