import { z } from "zod";

export const environmentFields = {
  name: z
    .string()
    .min(1, "Required")
    .max(50)
    .regex(/^[A-Z0-9_]+$/, "Upper-case letters, digits, underscores only (e.g. STAGING)"),
  description: z.string().max(500),
};
