"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { defaultAuthenticatedRoute } from "@/shared/routes/route-paths";
import { registerSchema, type RegisterInput } from "../model/auth.schema";
import { applyFormError, toRegisterFormError } from "../model/auth-errors";
import { useRegisterMutation } from "../model/auth.mutations";
import { AuthField } from "./auth-field";
import { AuthFormErrorAlert } from "./auth-form-error-alert";
import { AuthHeading } from "./auth-heading";
import { PasswordField } from "./password-field";

export function RegisterForm() {
  const router = useRouter();
  const mutation = useRegisterMutation();
  const [rootErrorMessage, setRootErrorMessage] = useState<string | null>(null);
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
    setRootErrorMessage(null);
    try {
      await mutation.mutateAsync(input);
      router.push(defaultAuthenticatedRoute);
      router.refresh();
    } catch (error) {
      const formError = toRegisterFormError(error);
      applyFormError(setError, formError);
      if (formError.generalError) {
        setRootErrorMessage(formError.generalError);
        toast.error(formError.generalError);
      }
    }
  }

  return (
    <>
      <AuthHeading eyebrow="Create account" title="Hello." description="" />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {rootErrorMessage || errors.root?.message ? (
          <AuthFormErrorAlert
            message={rootErrorMessage ?? errors.root?.message ?? ""}
          />
        ) : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <AuthField
            id="register-display-name"
            label="Display name"
            type="text"
            disabled={mutation.isPending}
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
            disabled={mutation.isPending}
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
          disabled={mutation.isPending}
          autoComplete="email"
          maxLength={255}
          required
          placeholder="you@example.com"
          error={errors.email?.message}
          {...register("email")}
        />

        <PasswordField
          id="register-password"
          disabled={mutation.isPending}
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
          {mutation.isPending ? (
            <>
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Creating account...
            </>
          ) : (
            "Create account"
          )}
        </Button>
      </form>
    </>
  );
}
