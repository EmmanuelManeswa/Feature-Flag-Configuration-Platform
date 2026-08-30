import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createFlag, deleteFlag, evaluateFlag, getFlag, getFlagAudit, listFlags, updateFlag } from "./api";
import type { CreateFeatureFlagRequest, UpdateFeatureFlagRequest } from "@/types/api";

export const flagKeys = {
  all: ["flags"] as const,
  list: (environmentId: string | undefined, page: number) => ["flags", "list", environmentId ?? "all", page] as const,
  detail: (id: string) => ["flags", "detail", id] as const,
  audit: (id: string, page: number) => ["flags", "audit", id, page] as const,
};

export function useFlags(params: { environmentId?: string; page: number; size?: number }) {
  return useQuery({
    queryKey: flagKeys.list(params.environmentId, params.page),
    queryFn: () => listFlags(params),
    placeholderData: keepPreviousData,
  });
}

export function useFlag(id: string | undefined) {
  return useQuery({
    queryKey: flagKeys.detail(id ?? ""),
    queryFn: () => getFlag(id as string),
    enabled: Boolean(id),
  });
}

export function useFlagAudit(id: string | undefined, page: number) {
  return useQuery({
    queryKey: flagKeys.audit(id ?? "", page),
    queryFn: () => getFlagAudit(id as string, { page, size: 10 }),
    enabled: Boolean(id),
  });
}

export function useCreateFlag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateFeatureFlagRequest) => createFlag(input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: flagKeys.all });
    },
  });
}

export function useUpdateFlag(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: UpdateFeatureFlagRequest) => updateFlag(id, input),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: flagKeys.all });
    },
  });
}

export function useDeleteFlag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteFlag(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: flagKeys.all });
    },
  });
}

export function useEvaluateFlag(id: string) {
  return useMutation({
    mutationFn: (input: { stableIdentifier: string; attributes: Record<string, string> }) => evaluateFlag(id, input),
  });
}
