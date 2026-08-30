"use client";

import { useState } from "react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { DataTable } from "@/components/data-table";
import { auditColumns } from "@/components/audit/audit-columns";
import { useAuditLogs } from "@/features/audit/hooks";
import { useEnvironments } from "@/features/environments/hooks";

export default function AuditLogPage() {
  const [environmentId, setEnvironmentId] = useState("all");
  const [page, setPage] = useState(0);
  const { data: environments } = useEnvironments();
  const auditQuery = useAuditLogs({ environmentId: environmentId === "all" ? undefined : environmentId, page });

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Audit Log</h1>
        <p className="text-sm text-muted-foreground">Immutable history of every feature flag change.</p>
      </div>

      <Select
        value={environmentId}
        onValueChange={(v) => {
          setEnvironmentId(v);
          setPage(0);
        }}
      >
        <SelectTrigger className="w-48">
          <SelectValue placeholder="All environments" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all">All environments</SelectItem>
          {environments?.map((env) => (
            <SelectItem key={env.id} value={env.id}>
              {env.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <DataTable
        columns={auditColumns}
        data={auditQuery.data?.content ?? []}
        isLoading={auditQuery.isLoading}
        emptyMessage="No audit entries yet."
        page={page}
        totalPages={auditQuery.data?.totalPages ?? 0}
        onPageChange={setPage}
      />
    </div>
  );
}
