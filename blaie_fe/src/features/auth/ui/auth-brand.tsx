import Image from "next/image";
import Link from "next/link";
import { routePaths } from "@/shared/routes/route-paths";

const organizedItems = [
  { label: "TASK", text: "Send the revised proposal", tone: "violet" },
  { label: "REMINDER", text: "Today, 3:30 PM", tone: "cyan" },
] as const;

export function AuthBrandMark({ compact = false }: { compact?: boolean }) {
  return (
    <Link
      href={routePaths.home}
      aria-label="Blaie home"
      className="group inline-flex w-fit items-center gap-3 rounded-[9.6px] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-4 focus-visible:ring-offset-background"
    >
      <Image
        src="/logo.svg"
        alt=""
        aria-hidden="true"
        width={28}
        height={28}
        className={compact ? "size-6" : "size-7"}
      />
      <span className="font-display-sm text-[22px] font-normal tracking-tight">
        Blaie
      </span>
    </Link>
  );
}

export function AuthBrandPanel() {
  return (
    <aside className="relative hidden min-h-[100dvh] overflow-hidden border-r border-border bg-background px-10 py-9 lg:flex lg:flex-col xl:px-14 xl:py-11">
      <div className="pointer-events-none absolute inset-0" aria-hidden="true">
        <div className="absolute -left-28 top-[28%] size-[26rem] rounded-full bg-brand-accent-soft blur-[110px]" />
        <div className="absolute bottom-[-12rem] right-[-10rem] size-[30rem] rounded-full bg-brand-accent-soft blur-[130px]" />
        <div className="absolute inset-0 bg-[linear-gradient(var(--grid-line)_1px,transparent_1px),linear-gradient(90deg,var(--grid-line)_1px,transparent_1px)] bg-[size:72px_72px] [mask-image:linear-gradient(to_bottom,black,transparent_82%)]" />
      </div>

      <div className="relative z-10">
        <AuthBrandMark />
      </div>

      <div className="relative z-10 my-auto max-w-[34rem] py-16">
        <h2 className="font-anthropic-serif max-w-[32rem] text-[clamp(2.75rem,4.5vw,4.85rem)] leading-[0.98] font-[330] tracking-[-0.055em] text-foreground">
          Clear the noise. Keep what matters.
        </h2>

        <div className="mt-12 max-w-[31rem] border-y border-border py-4">
          <div className="flex items-center gap-4 px-1 pb-4">
            <span className="grid size-8 shrink-0 place-items-center rounded-md border border-border bg-muted font-mono text-[10px] text-muted-foreground">
              IN
            </span>
            <p className="min-w-0 flex-1 truncate text-sm text-foreground">
              “Send the revised proposal and remind me at 3:30.”
            </p>
            <span className="size-1.5 shrink-0 rounded-full bg-brand-accent shadow-[0_0_16px_var(--accent-glow)]" />
          </div>

          <div className="ml-4 mb-4 h-5 border-l border-dashed border-border" />

          <div className="space-y-2 pt-1">
            {organizedItems.map((item) => (
              <div
                key={item.label}
                className="flex items-center gap-4 rounded-lg border border-border bg-card px-4 py-3.5"
              >
                <span className="font-mono text-[9px] tracking-[0.14em] text-foreground">
                  {item.label}
                </span>
                <span className="h-4 w-px bg-border" />
                <span className="text-[13px] text-foreground">
                  {item.text}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </aside>
  );
}
