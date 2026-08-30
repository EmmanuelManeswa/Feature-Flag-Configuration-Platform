"use client";

import Link from "next/link";
import { ChevronRight } from "lucide-react";
import type { ColumnDef } from "@/components/data-table";
import { FlagStatusBadge, FlagTypeBadge } from "@/components/flags/flag-badges";
import { Badge } from "@/components/ui/badge";
import { formatRelativeTime } from "@/lib/format";
import type { FeatureFlagDto } from "@/types/api";

export const flagsColumns: ColumnDef<FeatureFlagDto, unknown>[] = [
  {
    accessorKey: "key",
    header: "Flag",
    cell: ({ row }) => (
      <Link href={`/flags/${row.original.id}`} className="group flex items-center gap-1.5">
        <div>
          <div className="font-medium text-foreground group-hover:underline">{row.original.name}</div>
          <div className="font-mono text-xs text-muted-foreground">{row.original.key}</div>
        </div>
        <ChevronRight className="size-3.5 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
      </Link>
    ),
  },
  {
    accessorKey: "environmentName",
    header: "Environment",
    cell: ({ row }) => <Badge variant="secondary">{row.original.environmentName}</Badge>,
  },
  {
    accessorKey: "type",
    header: "Strategy",
    cell: ({ row }) => <FlagTypeBadge type={row.original.type} rolloutPercentage={row.original.rolloutPercentage} />,
  },
  {
    accessorKey: "enabled",
    header: "Status",
    cell: ({ row }) => <FlagStatusBadge enabled={row.original.enabled} />,
  },
  {
    accessorKey: "version",
    header: "Version",
    cell: ({ row }) => <span className="font-mono text-xs text-muted-foreground">v{row.original.version}</span>,
  },
  {
    accessorKey: "updatedAt",
    header: "Last modified",
    cell: ({ row }) => (
      <div className="text-sm">
        <div>{formatRelativeTime(row.original.updatedAt)}</div>
        <div className="text-xs text-muted-foreground">{row.original.updatedByEmail}</div>
      </div>
    ),
  },
];
