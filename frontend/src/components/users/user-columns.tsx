"use client";

import { Ban, CheckCircle2 } from "lucide-react";
import type { ColumnDef } from "@/components/data-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { FlagStatusBadge } from "@/components/flags/flag-badges";
import { formatDateTime } from "@/lib/format";
import type { UserDto } from "@/types/api";

interface UserColumnsOptions {
  currentUserId: string;
  pendingId: string | undefined;
  onDisable: (user: UserDto) => void;
  onEnable: (user: UserDto) => void;
}

export function getUserColumns({ currentUserId, pendingId, onDisable, onEnable }: UserColumnsOptions): ColumnDef<UserDto, unknown>[] {
  return [
    {
      accessorKey: "email",
      header: "User",
      cell: ({ row }) => (
        <div>
          <div className="font-medium text-foreground">{row.original.displayName}</div>
          <div className="text-xs text-muted-foreground">{row.original.email}</div>
        </div>
      ),
    },
    {
      accessorKey: "role",
      header: "Role",
      cell: ({ row }) => <Badge variant="secondary">{row.original.role}</Badge>,
    },
    {
      accessorKey: "enabled",
      header: "Status",
      cell: ({ row }) => <FlagStatusBadge enabled={row.original.enabled} />,
    },
    {
      accessorKey: "createdAt",
      header: "Created",
      cell: ({ row }) => <span className="text-sm text-muted-foreground">{formatDateTime(row.original.createdAt)}</span>,
    },
    {
      id: "actions",
      header: "",
      cell: ({ row }) => {
        const isSelf = row.original.id === currentUserId;
        const isPending = pendingId === row.original.id;

        if (isSelf) {
          // Server-side enforced too (POST /users/{id}/disable rejects
          // disabling your own account with a 400) — hiding the action here
          // is the UX nicety on top of that real boundary, not a substitute
          // for it.
          return <span className="text-xs text-muted-foreground">You</span>;
        }

        return row.original.enabled ? (
          <Button variant="outline" size="sm" disabled={isPending} onClick={() => onDisable(row.original)}>
            <Ban className="size-3.5" />
            Disable
          </Button>
        ) : (
          <Button variant="outline" size="sm" disabled={isPending} onClick={() => onEnable(row.original)}>
            <CheckCircle2 className="size-3.5" />
            Enable
          </Button>
        );
      },
    },
  ];
}
