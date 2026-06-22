"use client";

import { useState } from "react";
import { AuthField } from "./auth-field";

type PasswordFieldProps = {
  id: string;
  label?: string;
  hint?: string;
  autoComplete: "current-password" | "new-password";
};

export function PasswordField({
  id,
  label = "Password",
  hint,
  autoComplete,
}: PasswordFieldProps) {
  const [visible, setVisible] = useState(false);

  return (
    <AuthField
      id={id}
      name="password"
      label={label}
      type={visible ? "text" : "password"}
      autoComplete={autoComplete}
      minLength={8}
      maxLength={16}
      required
      placeholder="Enter your password"
      hint={hint}
      trailing={
        <button
          type="button"
          onClick={() => setVisible((current) => !current)}
          aria-label={visible ? "Hide password" : "Show password"}
          aria-pressed={visible}
          className="rounded-md px-2.5 py-2 text-xs font-medium text-warm-slate transition-colors hover:bg-warm-coal hover:text-ivory-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-dust-purple"
        >
          {visible ? "Hide" : "Show"}
        </button>
      }
    />
  );
}
