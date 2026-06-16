import { z } from "zod";

export const authCredentialSchema = z.object({
  identifier: z.string().min(1),
  password: z.string().min(1),
});

export const authSessionSchema = z.object({
  accessToken: z.string().nullable().optional(),
  refreshToken: z.string().nullable().optional(),
});
