"use client";

import { type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { AuthField } from "./auth-field";
import { AuthHeading } from "./auth-heading";
import { PasswordField } from "./password-field";

export function RegisterForm() {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
  }

  return (
    <>
      <AuthHeading
        eyebrow="Create account"
        title="Hello."
        description="Start with one quiet place for every thought, task, and reminder."
      />

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <AuthField
            id="register-display-name"
            name="displayName"
            label="Display name"
            type="text"
            autoComplete="name"
            maxLength={32}
            required
            autoFocus
            placeholder="Thien Doan"
          />
          <AuthField
            id="register-username"
            name="username"
            label="Username"
            type="text"
            autoComplete="username"
            minLength={3}
            maxLength={32}
            pattern="[A-Za-z0-9._-]+"
            required
            placeholder="thiendoan"
          />
        </div>

        <AuthField
          id="register-email"
          name="email"
          label="Email"
          type="email"
          autoComplete="email"
          maxLength={255}
          required
          placeholder="you@example.com"
        />

        <PasswordField
          id="register-password"
          autoComplete="new-password"
          hint="Use 8-16 characters with an uppercase letter and a special character."
        />

        <Button
          type="submit"
          className="mt-2 h-12 w-full text-[14px] font-semibold transition-[background-color,transform] active:scale-[0.99]"
        >
          Create account
        </Button>
      </form>
    </>
  );
}
