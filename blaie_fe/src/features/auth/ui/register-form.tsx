"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { defaultAuthenticatedRoute } from "@/shared/routes/route-paths";
import { registerSchema, type RegisterInput } from "../model/auth.schema";
import { applyFormError, toRegisterFormError } from "../model/auth-errors";
import { useRegisterMutation } from "../model/auth.mutations";
import { AuthField } from "./auth-field";
import { AuthHeading } from "./auth-heading";
import { PasswordField } from "./password-field";

export function RegisterForm() {
  const router = useRouter();
  const mutation = useRegisterMutation();
  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm<RegisterInput>({
    resolver: zodResolver(registerSchema),
    mode: "onSubmit",
    reValidateMode: "onChange",
  });

  async function onSubmit(input: RegisterInput) {
    clearErrors();
    try {
      const user = await mutation.mutateAsync(input);
      console.log("register success", user);
      router.push(defaultAuthenticatedRoute);
      router.refresh();
    } catch (error) {
      applyFormError(setError, toRegisterFormError(error));
    }
  }

  return (
    <>
      <AuthHeading
        eyebrow="Create account"
        title="Hello."
        description="Start with one quiet place for every thought, task, and reminder."
      />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {errors.root?.message ? (
          <p className="rounded-lg border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {errors.root.message}
          </p>
        ) : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <AuthField
            id="register-display-name"
            label="Display name"
            type="text"
            autoComplete="name"
            maxLength={32}
            required
            autoFocus
            placeholder="Thien Doan"
            error={errors.displayName?.message}
            {...register("displayName")}
          />
          <AuthField
            id="register-username"
            label="Username"
            type="text"
            autoComplete="username"
            minLength={3}
            maxLength={32}
            pattern="[A-Za-z0-9._-]+"
            required
            placeholder="thiendoan"
            error={errors.username?.message}
            {...register("username")}
          />
        </div>

        <AuthField
          id="register-email"
          label="Email"
          type="email"
          autoComplete="email"
          maxLength={255}
          required
          placeholder="you@example.com"
          error={errors.email?.message}
          {...register("email")}
        />

        <PasswordField
          id="register-password"
          autoComplete="new-password"
          hint="Use 8-16 characters with an uppercase letter and a special character."
          error={errors.password?.message}
          {...register("password")}
        />

        <Button
          type="submit"
          disabled={mutation.isPending}
          className="mt-2 h-12 w-full text-[14px] font-semibold transition-[background-color,transform] active:scale-[0.99]"
        >
          {mutation.isPending ? "Creating account..." : "Create account"}
        </Button>
      </form>
    </>
  );
}
