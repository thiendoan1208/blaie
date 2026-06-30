"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, Loader2 } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { isAppError } from "@/shared/api/errors/app-error";
import { routePaths } from "@/shared/routes/route-paths";
import {
  passwordResetConfirmSchema,
  passwordResetRequestSchema,
  type PasswordResetConfirmInput,
  type PasswordResetRequestInput,
} from "../model/auth.schema";
import {
  useConfirmPasswordResetMutation,
  useRequestPasswordResetMutation,
} from "../model/auth.mutations";
import { AuthField } from "./auth-field";
import { AuthFormErrorAlert } from "./auth-form-error-alert";
import { AuthHeading } from "./auth-heading";
import { PasswordField } from "./password-field";

type ResetStep = "request" | "confirm";

export function ForgotPasswordForm() {
  const router = useRouter();
  const requestMutation = useRequestPasswordResetMutation();
  const confirmMutation = useConfirmPasswordResetMutation();
  const [step, setStep] = useState<ResetStep>("request");
  const [rootErrorMessage, setRootErrorMessage] = useState<string | null>(null);

  const requestForm = useForm<PasswordResetRequestInput>({
    resolver: zodResolver(passwordResetRequestSchema),
    mode: "onSubmit",
    reValidateMode: "onChange",
  });
  const confirmForm = useForm<PasswordResetConfirmInput>({
    resolver: zodResolver(passwordResetConfirmSchema),
    mode: "onSubmit",
    reValidateMode: "onChange",
  });

  async function onRequestSubmit(input: PasswordResetRequestInput) {
    setRootErrorMessage(null);
    try {
      await requestMutation.mutateAsync(input);
      confirmForm.setValue("email", input.email, { shouldValidate: true });
      setStep("confirm");
      toast.success("If that email belongs to an account, we sent a reset code.");
    } catch (error) {
      handleRequestError(error);
    }
  }

  async function onConfirmSubmit(input: PasswordResetConfirmInput) {
    setRootErrorMessage(null);
    try {
      await confirmMutation.mutateAsync({
        email: input.email,
        code: input.code,
        newPassword: input.newPassword,
      });
      toast.success("Password reset successfully. Please sign in again.");
      router.push(routePaths.login);
      router.refresh();
    } catch (error) {
      handleConfirmError(error);
    }
  }

  function handleRequestError(error: unknown) {
    if (isAppError(error) && error.code === "VALIDATION_ERROR") {
      const emailError = error.fieldErrors?.email?.[0];
      if (emailError) {
        requestForm.setError("email", { type: "server", message: emailError });
        return;
      }
    }
    const message = isAppError(error) ? error.message : "Unable to send reset code. Please try again.";
    setRootErrorMessage(message);
    toast.error(message);
  }

  function handleConfirmError(error: unknown) {
    if (isAppError(error) && error.code === "VALIDATION_ERROR") {
      for (const field of ["email", "code", "newPassword"] as const) {
        const message = error.fieldErrors?.[field]?.[0];
        if (message) {
          confirmForm.setError(field, { type: "server", message });
        }
      }
      return;
    }

    if (isAppError(error) && error.code === "PASSWORD_RESET_INVALID_CODE") {
      confirmForm.setError("code", { type: "server", message: "The reset code is incorrect." });
      return;
    }

    if (isAppError(error) && error.code === "PASSWORD_RESET_EXPIRED") {
      confirmForm.setError("code", { type: "server", message: "The reset code has expired. Request a new one." });
      return;
    }

    if (isAppError(error) && error.code === "PASSWORD_RESET_TOO_MANY_ATTEMPTS") {
      const message = "Too many attempts. Request a new reset code.";
      setRootErrorMessage(message);
      toast.error(message);
      return;
    }

    const message = isAppError(error) ? error.message : "Unable to reset password. Please try again.";
    setRootErrorMessage(message);
    toast.error(message);
  }

  if (step === "confirm") {
    const confirmErrors = confirmForm.formState.errors;
    return (
      <>
        <AuthHeading
          eyebrow="Reset password"
          title="Enter your code."
          description="Use the six-digit code from your email and choose a new password."
        />

        {rootErrorMessage || confirmErrors.root?.message ? (
          <AuthFormErrorAlert message={rootErrorMessage ?? confirmErrors.root?.message ?? ""} />
        ) : null}

        <form onSubmit={confirmForm.handleSubmit(onConfirmSubmit)} className="space-y-5" noValidate>
          <AuthField
            id="reset-email"
            label="Email"
            type="email"
            disabled={confirmMutation.isPending}
            autoComplete="email"
            error={confirmErrors.email?.message}
            {...confirmForm.register("email")}
          />
          <AuthField
            id="reset-code"
            label="Reset code"
            type="text"
            disabled={confirmMutation.isPending}
            inputMode="numeric"
            maxLength={6}
            autoComplete="one-time-code"
            placeholder="123456"
            error={confirmErrors.code?.message}
            {...confirmForm.register("code")}
          />
          <PasswordField
            id="reset-new-password"
            label="New password"
            disabled={confirmMutation.isPending}
            autoComplete="new-password"
            error={confirmErrors.newPassword?.message}
            {...confirmForm.register("newPassword")}
          />
          <PasswordField
            id="reset-confirm-password"
            label="Confirm password"
            disabled={confirmMutation.isPending}
            autoComplete="new-password"
            error={confirmErrors.confirmPassword?.message}
            {...confirmForm.register("confirmPassword")}
          />

          <Button
            type="submit"
            disabled={confirmMutation.isPending}
            className="h-12 w-full text-[14px] font-semibold transition-[background-color,transform] active:scale-[0.99]"
          >
            {confirmMutation.isPending ? (
              <>
                <Loader2 className="size-4 animate-spin" aria-hidden="true" />
                Resetting password...
              </>
            ) : (
              "Reset password"
            )}
          </Button>
        </form>

        <button
          type="button"
          disabled={confirmMutation.isPending}
          onClick={() => {
            setRootErrorMessage(null);
            setStep("request");
          }}
          className="mt-5 inline-flex items-center gap-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          Use a different email
        </button>
      </>
    );
  }

  const requestErrors = requestForm.formState.errors;
  return (
    <>
      <AuthHeading
        eyebrow="Forgot password"
        title="Reset your password."
        description="Enter your account email and we will send a six-digit reset code."
      />

      {rootErrorMessage || requestErrors.root?.message ? (
        <AuthFormErrorAlert message={rootErrorMessage ?? requestErrors.root?.message ?? ""} />
      ) : null}

      <form onSubmit={requestForm.handleSubmit(onRequestSubmit)} className="space-y-5" noValidate>
        <AuthField
          id="forgot-email"
          label="Email"
          type="email"
          disabled={requestMutation.isPending}
          autoComplete="email"
          required
          autoFocus
          placeholder="you@example.com"
          error={requestErrors.email?.message}
          {...requestForm.register("email")}
        />

        <Button
          type="submit"
          disabled={requestMutation.isPending}
          className="h-12 w-full text-[14px] font-semibold transition-[background-color,transform] active:scale-[0.99]"
        >
          {requestMutation.isPending ? (
            <>
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
              Sending code...
            </>
          ) : (
            "Send reset code"
          )}
        </Button>
      </form>

      <Link
        href={routePaths.login}
        className="mt-5 inline-flex items-center gap-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Back to sign in
      </Link>
    </>
  );
}
