import { z } from "zod";

export const createUserFields = {
  email: z.string().min(1, "Required").email("Enter a valid email address"),
  displayName: z.string().min(1, "Required").max(255),
  role: z.enum(["ADMIN", "VIEWER"]),
};

export const changePasswordFields = {
  currentPassword: z.string().min(1, "Required"),
  newPassword: z.string().min(8, "At least 8 characters").max(100),
};
