import { apiFetch } from "@/lib/api-client";
import type { LoginResponse, UserSummary } from "@/types/api";

export function login(email: string, password: string): Promise<LoginResponse> {
  return apiFetch<LoginResponse>("/api/v1/auth/login", {
    method: "POST",
    body: { email, password },
  });
}

export function getCurrentUser(): Promise<UserSummary> {
  return apiFetch<UserSummary>("/api/v1/auth/me");
}
