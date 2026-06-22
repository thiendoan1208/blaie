import { describe, expect, it } from "vitest";
import { loginSchema, passwordSchema, registerSchema } from "./auth.schema";

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

describe("auth schemas", () => {
  it("normalizes login input", () => {
    expect(
      loginSchema.parse({ identifier: "  User@Example.com ", password: " Password1! " }),
    ).toEqual({ identifier: "User@Example.com", password: "Password1!" });
  });

  it("accepts a Unicode display name and trims registration input", () => {
    expect(
      registerSchema.parse({
        displayName: " Nguyễn Văn An ",
        username: " thien.doan ",
        email: " thien@example.com ",
        password: " Password1! ",
      }),
    ).toEqual({
      displayName: "Nguyễn Văn An",
      username: "thien.doan",
      email: "thien@example.com",
      password: "Password1!",
    });
  });

  it("rejects display names over 32 characters or with unsupported symbols", () => {
    const base = {
      username: "valid.user",
      email: "valid@example.com",
      password: "Password1!",
    };

    expect(registerSchema.safeParse({ ...base, displayName: "A".repeat(33) }).success).toBe(false);
    expect(registerSchema.safeParse({ ...base, displayName: "Blaie 🚀" }).success).toBe(false);
  });
});
