"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { defaultAuthenticatedRoute } from "@/shared/routes/route-paths";
import { loginSchema, type LoginInput } from "../model/auth.schema";
import { applyFormError, toLoginFormError } from "../model/auth-errors";
import { useLoginMutation } from "../model/auth.mutations";
import { AuthField } from "./auth-field";
import { AuthHeading } from "./auth-heading";
import { PasswordField } from "./password-field";

export function LoginForm() {
  const router = useRouter();
  const mutation = useLoginMutation();
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    mode: "onSubmit",
    reValidateMode: "onChange",
  });
  async function onSubmit(input: LoginInput) {
    clearErrors();
    try {
      const user = await mutation.mutateAsync(input);
      console.log("login success", user);
      router.push(defaultAuthenticatedRoute);
      router.refresh();
    } catch (error) {
      applyFormError(setError, toLoginFormError(error));
    }
  }

  return (
    <>
      <AuthHeading
        eyebrow="Sign in"
        title="Welcome back."
        description="Pick up where you left off and get back to what matters."
      />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
        {errors.root?.message ? (
          <p className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {errors.root.message}
          </p>
        ) : null}
        <AuthField
          id="login-identifier"
          label="Username or email"
          type="text"
          autoComplete="username"
          maxLength={255}
          required
          autoFocus
          placeholder="you@example.com"
          error={errors.identifier?.message}
          {...register("identifier")}
        />
        <PasswordField
          id="login-password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register("password")}
        />

        <Button
          type="submit"
          disabled={mutation.isPending}
          className="mt-2 h-12 w-full text-[14px] font-semibold transition-[background-color,transform] active:scale-[0.99]"
        >
          {mutation.isPending ? "Signing in..." : "Sign in"}
        </Button>
      </form>
    </>
  );
}
