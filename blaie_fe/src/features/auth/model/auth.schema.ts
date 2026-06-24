import { z } from "zod";

const USERNAME_PATTERN = /^[A-Za-z0-9._-]+$/;
const DISPLAY_NAME_PATTERN = /^[\p{L}\p{M}\p{N}][\p{L}\p{M}\p{N} .'-]*$/u;
const UPPERCASE_PATTERN = /[A-Z]/;
const SPECIAL_CHARACTER_PATTERN = /[^A-Za-z0-9\s]/;

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
  username: z
    .string()
    .trim()
    .min(1, "Enter a username.")
    .min(3, "Username must contain 3-32 characters.")
    .max(32, "Username must contain 3-32 characters.")
    .regex(
      USERNAME_PATTERN,
      "Use only letters, numbers, dots, underscores, or hyphens.",
    ),
  email: z
    .string()
    .trim()
    .min(1, "Enter your email.")
    .max(255, "Email must not exceed 255 characters.")
    .email("Enter a valid email address."),
  password: passwordSchema,
});

export type LoginInput = z.infer<typeof loginSchema>;
export type RegisterInput = z.infer<typeof registerSchema>;
