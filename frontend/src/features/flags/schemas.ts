import { z } from "zod";

// Mirrors backend Bean Validation exactly (featureflag/dto/*.java) — the
// backend remains authoritative; this is for fast client-side feedback only.

export const targetingRuleFields = {
  attribute: z
    .string()
    .min(1, "Required")
    .max(100)
    .regex(/^[a-zA-Z][a-zA-Z0-9_]*$/, "Letters, digits, underscore only, starting with a letter"),
  operator: z.enum(["EQUALS", "NOT_EQUALS"]),
  value: z.string().min(1, "Required").max(500),
};

export const targetingRuleSchema = z.object(targetingRuleFields);

export const flagFields = {
  key: z
    .string()
    .min(1, "Required")
    .max(100)
    .regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, "lower-kebab-case, e.g. new-dashboard"),
  name: z.string().min(1, "Required").max(255),
  description: z.string().max(1000),
  environmentId: z.string().min(1, "Select an environment"),
  rolloutPercentage: z.number().min(0, "0-100").max(100, "0-100"),
};
