import { apiFetch } from "@/lib/api-client";
import type { EnvironmentDto } from "@/types/api";

export function listEnvironments(): Promise<EnvironmentDto[]> {
  return apiFetch<EnvironmentDto[]>("/api/v1/environments");
}

export function createEnvironment(input: { name: string; description?: string }): Promise<EnvironmentDto> {
  return apiFetch<EnvironmentDto>("/api/v1/environments", { method: "POST", body: input });
}
