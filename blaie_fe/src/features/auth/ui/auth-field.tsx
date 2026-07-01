import { forwardRef, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "@/lib/utils";

export type AuthFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  trailing?: ReactNode;
};

export const AuthField = forwardRef<HTMLInputElement, AuthFieldProps>(function AuthField(
  { id, label, hint, error, trailing, className, ...props },
  ref,
) {
  const descriptionId = hint || error ? `${id}-description` : undefined;

  return (
    <div className="space-y-2">
      <label htmlFor={id} className="block text-[13px] font-medium text-foreground">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          ref={ref}
          aria-describedby={descriptionId}
          aria-invalid={error ? true : undefined}
          className={cn(
            "auth-input h-12 w-full rounded-lg border border-border bg-card px-4 text-[15px] text-foreground outline-none transition-[border-color,background-color,box-shadow] placeholder:text-muted-foreground hover:border-ring focus:border-ring focus:bg-card focus-visible:ring-2 focus-visible:ring-ring/30 disabled:cursor-not-allowed disabled:opacity-60 aria-invalid:border-destructive aria-invalid:focus:border-destructive aria-invalid:focus-visible:ring-destructive/20",
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
          role={error ? "alert" : undefined}
          aria-live={error ? "polite" : undefined}
          className={cn("text-xs leading-5", error ? "text-destructive" : "text-muted-foreground")}
        >
          {error ?? hint}
        </p>
      ) : null}
    </div>
  );
});
