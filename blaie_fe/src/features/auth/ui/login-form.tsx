"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { defaultAuthenticatedRoute, routePaths } from "@/shared/routes/route-paths";
import { loginSchema, type LoginInput } from "../model/auth.schema";
import { applyFormError, toLoginFormError } from "../model/auth-errors";
import { useLoginMutation } from "../model/auth.mutations";
import { AuthField } from "./auth-field";
import { AuthFormErrorAlert } from "./auth-form-error-alert";
import { AuthHeading } from "./auth-heading";
import { GoogleAuthButton } from "./google-auth-button";
import { PasswordField } from "./password-field";

type LoginFormProps = {
  googleAuthFailed?: boolean;
  nextPath?: string;
};

export function LoginForm({ googleAuthFailed = false, nextPath = defaultAuthenticatedRoute }: LoginFormProps) {
  const router = useRouter();
  const mutation = useLoginMutation();
  const [rootErrorMessage, setRootErrorMessage] = useState<string | null>(null);
  const googleErrorMessage =
    googleAuthFailed
      ? "Google sign-in could not be completed. Please try again."
      : null;
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
    setRootErrorMessage(null);
    try {
      const user = await mutation.mutateAsync(input);
      router.push(user.emailVerified ? defaultAuthenticatedRoute : routePaths.verifyEmail);
      router.refresh();
    } catch (error) {
      const formError = toLoginFormError(error);
      applyFormError(setError, formError);
      if (formError.generalError) {
        setRootErrorMessage(formError.generalError);
        toast.error(formError.generalError);
      }
    }
  }

  return (
    <>
      <AuthHeading eyebrow="Sign in" title="Welcome back." description="" />

      <div className="space-y-5">
        {rootErrorMessage || errors.root?.message || googleErrorMessage ? (
          <AuthFormErrorAlert
            message={rootErrorMessage ?? errors.root?.message ?? googleErrorMessage ?? ""}
          />
        ) : null}

        <GoogleAuthButton nextPath={nextPath} disabled={mutation.isPending} />

        <div className="flex items-center gap-3 text-xs text-stone-gray">
          <span className="h-px flex-1 bg-graphite-border" />
          <span>or</span>
          <span className="h-px flex-1 bg-graphite-border" />
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
        <AuthField
          id="login-identifier"
          label="Username or email"
          type="text"
          disabled={mutation.isPending}
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
          disabled={mutation.isPending}
          autoComplete="current-password"
          error={errors.password?.message}
          {...register("password")}
        />

        <div className="-mt-3 flex justify-end">
          <Link
            href={routePaths.forgotPassword}
            className="text-sm font-medium text-stone-gray transition-colors hover:text-ivory-text focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-dust-purple"
          >
            Forgot password?
          </Link>
        </div>

        <Button
          type="submit"
          disabled={mutation.isPending}
          className="mt-2 h-12 w-full text-[14px] font-semibold transition-[background-color,transform] active:scale-[0.99]"
        >
          {mutation.isPending ? (
            <>
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Signing in...
            </>
          ) : (
            "Sign in"
          )}
        </Button>
      </form>
    </>
  );
}
