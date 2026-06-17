import { z } from "zod";

export const requestIdSchema = z.string().min(1);

export const cursorPaginationSchema = z.object({
  cursor: z.string().trim().min(1).optional(),
  limit: z.coerce.number().int().positive().max(50).default(20),
});
