import { apiFetch } from "@/lib/api-client";
import type {
  AuditLogDto,
  CreateFeatureFlagRequest,
  EvaluationMetricsDto,
  EvaluationResultDto,
  FeatureFlagDto,
  Page,
  UpdateFeatureFlagRequest,
} from "@/types/api";

export function listFlags(params: { environmentId?: string; page?: number; size?: number }): Promise<Page<FeatureFlagDto>> {
  return apiFetch<Page<FeatureFlagDto>>("/api/v1/flags", {
    searchParams: { environmentId: params.environmentId, page: params.page, size: params.size ?? 20 },
  });
}

export function getFlag(id: string): Promise<FeatureFlagDto> {
  return apiFetch<FeatureFlagDto>(`/api/v1/flags/${id}`);
}

export function createFlag(input: CreateFeatureFlagRequest): Promise<FeatureFlagDto> {
  return apiFetch<FeatureFlagDto>("/api/v1/flags", { method: "POST", body: input });
}

export function updateFlag(id: string, input: UpdateFeatureFlagRequest): Promise<FeatureFlagDto> {
  return apiFetch<FeatureFlagDto>(`/api/v1/flags/${id}`, { method: "PUT", body: input });
}

export function deleteFlag(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/flags/${id}`, { method: "DELETE" });
}

export function evaluateFlag(
  id: string,
  input: { stableIdentifier: string; attributes: Record<string, string> },
): Promise<EvaluationResultDto> {
  return apiFetch<EvaluationResultDto>(`/api/v1/flags/${id}/evaluate`, { method: "POST", body: input });
}

export function getFlagAudit(id: string, params: { page?: number; size?: number }): Promise<Page<AuditLogDto>> {
  return apiFetch<Page<AuditLogDto>>(`/api/v1/flags/${id}/audit`, { searchParams: params });
}

export function getFlagMetrics(id: string): Promise<EvaluationMetricsDto> {
  return apiFetch<EvaluationMetricsDto>(`/api/v1/flags/${id}/metrics`);
}
