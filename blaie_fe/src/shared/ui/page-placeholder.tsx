import Link from "next/link";
import type { ReactNode } from "react";

type PlaceholderLink = {
  href: string;
  label: string;
};

type PagePlaceholderProps = {
  title: string;
  description: string;
  links?: PlaceholderLink[];
  children?: ReactNode;
};

export function PagePlaceholder({ title, description, links = [], children }: PagePlaceholderProps) {
  return (
    <section className="mx-auto flex max-w-3xl flex-col gap-4 rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
        <p className="text-sm leading-6 text-zinc-600">{description}</p>
      </div>
      {children}
      {links.length > 0 ? (
        <div className="flex flex-wrap gap-3 text-sm font-medium">
          {links.map((link) => (
            <Link key={link.href} className="rounded-lg border border-zinc-200 px-3 py-2" href={link.href}>
              {link.label}
            </Link>
          ))}
        </div>
      ) : null}
    </section>
  );
}
