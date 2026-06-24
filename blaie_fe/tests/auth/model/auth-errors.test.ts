import { describe, expect, it, vi } from "vitest";
import { createAppError } from "@/shared/api/errors/app-error";
import {
  applyFormError,
  toLoginFormError,
  toRegisterFormError,
} from "@/features/auth/model/auth-errors";

describe("toLoginFormError", () => {
  it("maps invalid credentials and forbidden errors to general errors", () => {
    expect(
      toLoginFormError(createAppError({ code: "INVALID_CREDENTIALS", status: 401, message: "Invalid" })),
    ).toEqual({
      fieldErrors: {},
      generalError: "Username, email, or password is incorrect.",
    });

    expect(
      toLoginFormError(createAppError({ code: "FORBIDDEN", status: 403, message: "Forbidden" })),
    ).toEqual({
      fieldErrors: {},
      generalError: "This account cannot access Blaie right now.",
    });
  });

  it("maps validation field errors", () => {
    const result = toLoginFormError(
      createAppError({
        code: "VALIDATION_ERROR",
        status: 422,
        message: "Validation failed",
        fieldErrors: {
          identifier: ["Identifier is required"],
          password: ["Password is required"],
        },
      }),
    );

    expect(result).toEqual({
      fieldErrors: {
        identifier: "Identifier is required",
        password: "Password is required",
      },
      generalError: undefined,
    });
  });

  it("uses a fallback for non-AppError values", () => {
    expect(toLoginFormError(new Error("boom"))).toEqual({
      fieldErrors: {},
      generalError: "Something went wrong. Please try again.",
    });
  });
});

describe("toRegisterFormError", () => {
  it("maps duplicate username and email errors to field errors", () => {
    expect(
      toRegisterFormError(createAppError({ code: "USERNAME_ALREADY_EXISTS", status: 409, message: "Duplicate" })),
    ).toEqual({ fieldErrors: { username: "Username already exists." } });

    expect(
      toRegisterFormError(createAppError({ code: "EMAIL_ALREADY_EXISTS", status: 409, message: "Duplicate" })),
    ).toEqual({ fieldErrors: { email: "Email already exists." } });
  });

  it("maps validation field errors", () => {
    const result = toRegisterFormError(
      createAppError({
        code: "VALIDATION_ERROR",
        status: 422,
        message: "Validation failed",
        fieldErrors: {
          displayName: ["Display name is required"],
          username: ["Username is required"],
          email: ["Email is invalid"],
          password: ["Password is required"],
        },
      }),
    );

    expect(result.fieldErrors).toEqual({
      displayName: "Display name is required",
      username: "Username is required",
      email: "Email is invalid",
      password: "Password is required",
    });
  });
});

describe("applyFormError", () => {
  it("applies field and root errors through setError", () => {
    const setError = vi.fn();

    applyFormError(setError, {
      fieldErrors: {
        email: "Email already exists.",
        username: "",
      },
      generalError: "Please try again.",
    });

    expect(setError).toHaveBeenCalledWith("email", {
      type: "server",
      message: "Email already exists.",
    });
    expect(setError).toHaveBeenCalledWith("root", {
      type: "server",
      message: "Please try again.",
    });
    expect(setError).toHaveBeenCalledTimes(2);
  });
});
