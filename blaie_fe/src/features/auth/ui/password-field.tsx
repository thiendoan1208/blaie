"use client";

import { forwardRef, useState } from "react";
import { AuthField } from "./auth-field";

type PasswordFieldProps = Omit<
  React.ComponentPropsWithoutRef<typeof AuthField>,
  "type" | "trailing" | "label"
> & {
  label?: string;
  autoComplete: "current-password" | "new-password";
};

export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(
  function PasswordField(
    { id, label = "Password", hint, error, autoComplete, disabled, ...props },
    ref,
  ) {
    const [visible, setVisible] = useState(false);

    return (
      <AuthField
        id={id}
        ref={ref}
        name="password"
        label={label}
        type={visible ? "text" : "password"}
        autoComplete={autoComplete}
        placeholder="Enter your password"
        hint={hint}
        error={error}
        disabled={disabled}
        trailing={
          <button
            type="button"
            disabled={disabled}
            onClick={() => setVisible((current) => !current)}
            aria-label={visible ? "Hide password" : "Show password"}
            aria-pressed={visible}
            className="rounded-md px-2.5 py-2 text-xs font-medium text-warm-slate transition-colors hover:bg-warm-coal hover:text-ivory-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-dust-purple disabled:pointer-events-none disabled:opacity-50"
          >
            {visible ? "Hide" : "Show"}
          </button>
        }
        {...props}
      />
    );
  }
);

PasswordField.displayName = "PasswordField";
