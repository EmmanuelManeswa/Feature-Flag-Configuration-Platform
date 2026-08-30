"use client";

import type { ColumnDef } from "@/components/data-table";
import { ActionBadge } from "@/components/audit/action-badge";
import { AuditDiff } from "@/components/audit/audit-diff";
import { Badge } from "@/components/ui/badge";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { formatDateTime, formatRelativeTime } from "@/lib/format";
import type { AuditLogDto } from "@/types/api";

export const auditColumns: ColumnDef<AuditLogDto, unknown>[] = [
  {
    accessorKey: "action",
    header: "Action",
    cell: ({ row }) => <ActionBadge action={row.original.action} />,
  },
  {
    accessorKey: "actorEmail",
    header: "Actor",
    cell: ({ row }) => <span className="text-sm">{row.original.actorEmail}</span>,
  },
  {
    accessorKey: "entityType",
    header: "Resource",
    cell: ({ row }) => (
      <span className="font-mono text-xs text-muted-foreground">
        {row.original.entityType} · {row.original.entityId.slice(0, 8)}
      </span>
    ),
  },
  {
    accessorKey: "correlationId",
    header: "Correlation ID",
    cell: ({ row }) => (
      <Badge variant="outline" className="font-mono text-[10px]">
        {row.original.correlationId.slice(0, 8)}
      </Badge>
    ),
  },
  {
    accessorKey: "createdAt",
    header: "When",
    cell: ({ row }) => (
      <span className="text-sm text-muted-foreground" title={formatDateTime(row.original.createdAt)}>
        {formatRelativeTime(row.original.createdAt)}
      </span>
    ),
  },
  {
    id: "diff",
    header: "Details",
    cell: ({ row }) => (
      <Popover>
        <PopoverTrigger asChild>
          <Button variant="ghost" size="sm">
            View diff
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-80" align="end">
          <AuditDiff
            action={row.original.action}
            previousValue={row.original.previousValue}
            newValue={row.original.newValue}
          />
        </PopoverContent>
      </Popover>
    ),
  },
];
