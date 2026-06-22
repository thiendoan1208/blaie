"use client";

import { type FormEvent } from "react";
import { Button } from "@/components/ui/button";
import { AuthField } from "./auth-field";
import { AuthHeading } from "./auth-heading";
import { PasswordField } from "./password-field";

export function LoginForm() {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
  }

  return (
    <>
      <AuthHeading
        eyebrow="Sign in"
        title="Welcome back."
        description="Pick up where you left off and get back to what matters."
      />

      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        <AuthField
          id="login-identifier"
          name="identifier"
          label="Username or email"
          type="text"
          autoComplete="username"
          maxLength={255}
          required
          autoFocus
          placeholder="you@example.com"
        />
        <PasswordField id="login-password" autoComplete="current-password" />

        <Button
          type="submit"
          className="mt-2 h-12 w-full text-[14px] font-semibold transition-[background-color,transform] active:scale-[0.99]"
        >
          Sign in
        </Button>
      </form>
    </>
  );
}
