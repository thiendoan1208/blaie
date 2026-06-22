import type { InputHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

type AuthFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  trailing?: ReactNode;
};

export function AuthField({
  id,
  label,
  hint,
  error,
  trailing,
  className,
  ...props
}: AuthFieldProps) {
  const descriptionId = hint || error ? `${id}-description` : undefined;

  return (
    <div className="space-y-2">
      <label htmlFor={id} className="block text-[13px] font-medium text-parchment-white">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          aria-describedby={descriptionId}
          aria-invalid={error ? true : undefined}
          className={cn(
            "auth-input h-12 w-full rounded-lg border border-graphite-border bg-charcoal-surface px-4 text-[15px] text-ivory-text outline-none transition-[border-color,background-color] placeholder:text-stone-gray hover:border-cool-stone focus:border-ivory-text focus:bg-charcoal-surface aria-invalid:border-destructive",
            trailing && "pr-16",
            className,
          )}
          {...props}
        />
        {trailing ? (
          <div className="absolute inset-y-0 right-1.5 flex items-center">{trailing}</div>
        ) : null}
      </div>
      {error || hint ? (
        <p
          id={descriptionId}
          className={cn("text-xs leading-5", error ? "text-destructive" : "text-stone-gray")}
        >
          {error ?? hint}
        </p>
      ) : null}
    </div>
  );
}
