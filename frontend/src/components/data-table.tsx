"use client";

import { flexRender, type RowData } from "@tanstack/react-table";
import {
  getCoreRowModel,
  useLegacyTable as useReactTable,
  type LegacyColumnDef as ColumnDef,
} from "@tanstack/react-table/legacy";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * TanStack Table v9 replaces `useReactTable` with a new atom-store-based
 * `useTable` hook (a much larger API surface: explicit per-feature row-model
 * factories, `table.Subscribe`/`table.atoms.*` for state, etc.). It also
 * ships `useLegacyTable` as a first-party, fully-supported compatibility
 * layer matching the well-established v8 API. Given this project's scope
 * only needs core row rendering (pagination and filtering are server-side,
 * driven by this project's own query params, not TanStack Table's row
 * models), the legacy hook was the pragmatic choice: a well-understood,
 * low-risk API surface over spending the time budget learning and
 * debugging the new atom-based one for no functional benefit here.
 */
export { getCoreRowModel, useReactTable };
export type { ColumnDef };

interface DataTableProps<TData extends RowData> {
  columns: ColumnDef<TData, unknown>[];
  data: TData[];
  isLoading?: boolean;
  emptyMessage?: string;
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function DataTable<TData extends RowData>({
  columns,
  data,
  isLoading,
  emptyMessage = "No results.",
  page,
  totalPages,
  onPageChange,
}: DataTableProps<TData>) {
  const table = useReactTable({
    data,
    columns,
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <div className="space-y-3">
      <div className="overflow-x-auto rounded-md border border-border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id}>
                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {isLoading ? (
              Array.from({ length: 5 }).map((_, i) => (
                <TableRow key={i}>
                  {columns.map((_, j) => (
                    <TableCell key={j}>
                      <Skeleton className="h-5 w-full" />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            ) : table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="h-28 text-center text-sm text-muted-foreground">
                  {emptyMessage}
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {totalPages > 1 ? (
        <div className="flex items-center justify-between">
          <span className="text-xs text-muted-foreground">
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex gap-1.5">
            <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
              <ChevronLeft className="size-4" />
              Previous
            </Button>
            <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
              Next
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
