import { apiFetch } from "@/lib/api-client";
import type { ChangePasswordRequest, CreateUserRequest, CreatedUserDto, Page, UserDto } from "@/types/api";

export function listUsers(params: { page?: number; size?: number }): Promise<Page<UserDto>> {
  return apiFetch<Page<UserDto>>("/api/v1/users", { searchParams: { page: params.page, size: params.size ?? 20 } });
}

export function createUser(input: CreateUserRequest): Promise<CreatedUserDto> {
  return apiFetch<CreatedUserDto>("/api/v1/users", { method: "POST", body: input });
}

export function disableUser(id: string): Promise<UserDto> {
  return apiFetch<UserDto>(`/api/v1/users/${id}/disable`, { method: "POST" });
}

export function enableUser(id: string): Promise<UserDto> {
  return apiFetch<UserDto>(`/api/v1/users/${id}/enable`, { method: "POST" });
}

export function changeOwnPassword(input: ChangePasswordRequest): Promise<void> {
  return apiFetch<void>("/api/v1/auth/me/password", { method: "PUT", body: input });
}
