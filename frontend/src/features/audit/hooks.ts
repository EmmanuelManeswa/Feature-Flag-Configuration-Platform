import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { listAuditLogs } from "./api";

export function useAuditLogs(params: { environmentId?: string; page: number; size?: number }) {
  return useQuery({
    queryKey: ["audit-logs", params.environmentId ?? "all", params.page],
    queryFn: () => listAuditLogs(params),
    placeholderData: keepPreviousData,
  });
}
