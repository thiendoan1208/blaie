import { describe, expect, it } from "vitest";
import {
  loginSchema,
  passwordResetConfirmSchema,
  passwordResetRequestSchema,
  passwordSchema,
  registerSchema,
} from "@/features/auth/model/auth.schema";

describe("passwordSchema", () => {
  it("trims and accepts passwords at both length boundaries", () => {
    expect(passwordSchema.parse(" Upper!aa ")).toBe("Upper!aa");
    expect(passwordSchema.parse("Uppercase!123456")).toBe("Uppercase!123456");
  });

  it("rejects passwords outside 8-16 characters", () => {
    expect(passwordSchema.safeParse("Upper!a").success).toBe(false);
    expect(passwordSchema.safeParse("Uppercase!1234567").success).toBe(false);
  });

  it("requires an uppercase letter and a special character", () => {
    expect(passwordSchema.safeParse("lowercase!1").success).toBe(false);
    expect(passwordSchema.safeParse("Password12").success).toBe(false);
  });
});

describe("loginSchema", () => {
  it("normalizes login input", () => {
    expect(
      loginSchema.parse({ identifier: "  User@Example.com ", password: " Password1! " }),
    ).toEqual({ identifier: "User@Example.com", password: "Password1!" });
  });

  it("rejects empty values and overly long identifiers", () => {
    expect(loginSchema.safeParse({ identifier: "", password: "Password1!" }).success).toBe(false);
    expect(loginSchema.safeParse({ identifier: "user@example.com", password: "   " }).success).toBe(false);
    expect(loginSchema.safeParse({ identifier: "a".repeat(256), password: "Password1!" }).success).toBe(false);
  });
});

describe("registerSchema", () => {
  it("accepts a Unicode display name and trims registration input", () => {
    expect(
      registerSchema.parse({
        displayName: " Nguyen Van An ",
        username: " thien.doan ",
        email: " thien@example.com ",
        password: " Password1! ",
      }),
    ).toEqual({
      displayName: "Nguyen Van An",
      username: "thien.doan",
      email: "thien@example.com",
      password: "Password1!",
    });
  });

  it("rejects invalid display names", () => {
    const base = {
      username: "valid.user",
      email: "valid@example.com",
      password: "Password1!",
    };

    expect(registerSchema.safeParse({ ...base, displayName: "A".repeat(33) }).success).toBe(false);
    expect(registerSchema.safeParse({ ...base, displayName: "Blaie!" }).success).toBe(false);
  });

  it("rejects invalid usernames and emails", () => {
    const base = {
      displayName: "Blaie User",
      password: "Password1!",
    };

    expect(registerSchema.safeParse({ ...base, username: "ab", email: "valid@example.com" }).success).toBe(false);
    expect(registerSchema.safeParse({ ...base, username: "a".repeat(33), email: "valid@example.com" }).success).toBe(false);
    expect(registerSchema.safeParse({ ...base, username: "bad name", email: "valid@example.com" }).success).toBe(false);
    expect(registerSchema.safeParse({ ...base, username: "valid.user", email: "invalid-email" }).success).toBe(false);
  });
});

describe("password reset schemas", () => {
  it("normalizes request email and accepts valid reset confirmation", () => {
    expect(passwordResetRequestSchema.parse({ email: " User@Example.com " })).toEqual({
      email: "User@Example.com",
    });
    expect(passwordResetConfirmSchema.parse({
      email: " User@Example.com ",
      code: " 123456 ",
      newPassword: " Password2@ ",
      confirmPassword: " Password2@ ",
    })).toEqual({
      email: "User@Example.com",
      code: "123456",
      newPassword: "Password2@",
      confirmPassword: "Password2@",
    });
  });

  it("rejects invalid reset code and mismatched passwords", () => {
    const base = {
      email: "user@example.com",
      newPassword: "Password2@",
      confirmPassword: "Password2@",
    };

    expect(passwordResetConfirmSchema.safeParse({ ...base, code: "12345" }).success).toBe(false);
    expect(passwordResetConfirmSchema.safeParse({
      ...base,
      code: "123456",
      confirmPassword: "Password3@",
    }).success).toBe(false);
  });
});
