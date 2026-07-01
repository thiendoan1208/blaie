import { z } from "zod";

const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;
const DISPLAY_NAME_PATTERN = /^[\p{L}\p{M}\p{N}][\p{L}\p{M}\p{N} .'-]*$/u;
const UPPERCASE_PATTERN = /[A-Z]/;
const SPECIAL_CHARACTER_PATTERN = /[^A-Za-z0-9\s]/;

export const emailSchema = z
  .string()
  .trim()
  .min(1, "Enter your email.")
  .max(255, "Email must not exceed 255 characters.")
  .email("Enter a valid email address.");

export const passwordSchema = z
  .string()
  .trim()
  .min(1, "Password is required.")
  .min(8, "Password must contain 8-16 characters.")
  .max(16, "Password must contain 8-16 characters.")
  .regex(
    UPPERCASE_PATTERN,
    "Password must include at least one uppercase letter.",
  )
  .regex(
    SPECIAL_CHARACTER_PATTERN,
    "Password must include at least one special character.",
  );

export const usernameSchema = z
  .string()
  .trim()
  .min(1, "Enter a username.")
  .min(3, "Username must contain 3-32 characters.")
  .max(32, "Username must contain 3-32 characters.")
  .regex(
    USERNAME_PATTERN,
    "Use only letters, numbers, dots, underscores, or hyphens.",
  );

export const loginSchema = z.object({
  identifier: z
    .string()
    .trim()
    .min(1, "Enter your username or email.")
    .max(255, "Username or email must not exceed 255 characters."),
  password: z
    .string()
    .trim()
    .min(1, "Password is required."),
});

export const registerSchema = z.object({
  displayName: z
    .string()
    .trim()
    .min(1, "Enter your display name.")
    .max(32, "Display name must not exceed 32 characters.")
    .regex(
      DISPLAY_NAME_PATTERN,
      "Use only letters, numbers, spaces, periods, apostrophes, or hyphens.",
    ),
  username: usernameSchema,
  email: emailSchema,
  password: passwordSchema,
});

export const updateUsernameSchema = z.object({
  username: usernameSchema,
});

export const updatePasswordSchema = z
  .object({
    currentPassword: z.string().trim().optional(),
    newPassword: passwordSchema,
    confirmPassword: z.string().trim().min(1, "Confirm your new password."),
  })
  .refine((input) => input.newPassword === input.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match.",
  });

export const passwordResetRequestSchema = z.object({
  email: emailSchema,
});

export const passwordResetConfirmSchema = z
  .object({
    email: emailSchema,
    code: z
      .string()
      .trim()
      .min(1, "Enter the reset code.")
      .regex(/^[0-9]{6}$/, "Reset code must contain 6 digits."),
    newPassword: passwordSchema,
    confirmPassword: z.string().trim().min(1, "Confirm your new password."),
  })
  .refine((input) => input.newPassword === input.confirmPassword, {
    path: ["confirmPassword"],
    message: "Passwords do not match.",
  });

export type LoginInput = z.infer<typeof loginSchema>;
export type RegisterInput = z.infer<typeof registerSchema>;
export type UpdateUsernameInput = z.infer<typeof updateUsernameSchema>;
export type UpdatePasswordInput = z.infer<typeof updatePasswordSchema>;
export type PasswordResetRequestInput = z.infer<typeof passwordResetRequestSchema>;
export type PasswordResetConfirmInput = z.infer<typeof passwordResetConfirmSchema>;
