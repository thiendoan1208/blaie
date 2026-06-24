import type { FieldPath, FieldValues, UseFormSetError } from "react-hook-form";
import { isAppError } from "@/shared/api/errors/app-error";

type FieldErrorMap<TFields extends string> = Partial<Record<TFields, string>>;

export type AuthFormError<TFields extends string> = {
  fieldErrors: FieldErrorMap<TFields>;
  generalError?: string;
};

export function toLoginFormError(error: unknown): AuthFormError<"identifier" | "password"> {
  if (!isAppError(error)) {
    return { fieldErrors: {}, generalError: "Something went wrong. Please try again." };
  }

  if (error.code === "VALIDATION_ERROR") {
    return {
      fieldErrors: {
        identifier: error.fieldErrors?.identifier?.[0],
        password: error.fieldErrors?.password?.[0],
      },
      generalError: error.fieldErrors ? undefined : error.message,
    };
  }

  if (error.code === "INVALID_CREDENTIALS") {
    return { fieldErrors: {}, generalError: "Username, email, or password is incorrect." };
  }

  if (error.code === "FORBIDDEN") {
    return { fieldErrors: {}, generalError: "This account cannot access Blaie right now." };
  }

  return { fieldErrors: {}, generalError: error.message };
}

export function toRegisterFormError(
  error: unknown,
): AuthFormError<"displayName" | "username" | "email" | "password"> {
  if (!isAppError(error)) {
    return { fieldErrors: {}, generalError: "Something went wrong. Please try again." };
  }

  if (error.code === "VALIDATION_ERROR") {
    return {
      fieldErrors: {
        displayName: error.fieldErrors?.displayName?.[0],
        username: error.fieldErrors?.username?.[0],
        email: error.fieldErrors?.email?.[0],
        password: error.fieldErrors?.password?.[0],
      },
      generalError: error.fieldErrors ? undefined : error.message,
    };
  }

  if (error.code === "USERNAME_ALREADY_EXISTS") {
    return { fieldErrors: { username: "Username already exists." } };
  }

  if (error.code === "EMAIL_ALREADY_EXISTS") {
    return { fieldErrors: { email: "Email already exists." } };
  }

  return { fieldErrors: {}, generalError: error.message };
}

export function applyFormError<TValues extends FieldValues>(
  setError: UseFormSetError<TValues>,
  error: AuthFormError<Extract<keyof TValues, string>>,
) {
  for (const [field, message] of Object.entries(error.fieldErrors)) {
    if (typeof message === "string" && message.length > 0) {
      setError(field as FieldPath<TValues>, { type: "server", message });
    }
  }

  if (error.generalError) {
    setError("root", { type: "server", message: error.generalError });
  }
}
