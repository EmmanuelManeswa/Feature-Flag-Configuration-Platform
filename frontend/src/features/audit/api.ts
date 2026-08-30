import { apiFetch } from "@/lib/api-client";
import type { AuditLogDto, Page } from "@/types/api";

export function listAuditLogs(params: { environmentId?: string; page?: number; size?: number }): Promise<Page<AuditLogDto>> {
  return apiFetch<Page<AuditLogDto>>("/api/v1/audit-logs", { searchParams: params });
}
