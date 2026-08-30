"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Loader2, Pencil, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { FlagStatusBadge, FlagTypeBadge } from "@/components/flags/flag-badges";
import { FlagFormDialog } from "@/components/flags/flag-form-dialog";
import { EvaluationPlayground } from "@/components/evaluation/evaluation-playground";
import { DataTable } from "@/components/data-table";
import { auditColumns } from "@/components/audit/audit-columns";
import { useDeleteFlag, useFlag, useFlagAudit } from "@/features/flags/hooks";
import { useAuth } from "@/providers/auth-provider";
import { formatDateTime } from "@/lib/format";
import { ApiError } from "@/lib/api-client";

export default function FlagDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";

  const flagQuery = useFlag(id);
  const [auditPage, setAuditPage] = useState(0);
  const auditQuery = useFlagAudit(id, auditPage);
  const deleteFlag = useDeleteFlag();

  const [editOpen, setEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);

  if (flagQuery.isLoading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (flagQuery.isError || !flagQuery.data) {
    return (
      <div className="flex flex-col items-center gap-2 py-16 text-center">
        <p className="text-sm font-medium">Flag not found</p>
        <p className="text-sm text-muted-foreground">It may have been deleted, or the link is incorrect.</p>
        <Button variant="outline" size="sm" onClick={() => router.push("/flags")} className="mt-2">
          Back to flags
        </Button>
      </div>
    );
  }

  const flag = flagQuery.data;

  async function handleDelete() {
    try {
      await deleteFlag.mutateAsync(flag.id);
      toast.success("Flag deleted", { description: flag.name });
      router.push("/flags");
    } catch (error) {
      const message = error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
      toast.error("Delete failed", { description: message });
    } finally {
      setDeleteOpen(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">{flag.name}</h1>
            <FlagStatusBadge enabled={flag.enabled} />
          </div>
          <p className="mt-1 font-mono text-sm text-muted-foreground">{flag.key}</p>
        </div>
        {isAdmin && (
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setEditOpen(true)}>
              <Pencil className="size-4" />
              Edit
            </Button>
            <Button variant="outline" className="text-destructive hover:text-destructive" onClick={() => setDeleteOpen(true)}>
              <Trash2 className="size-4" />
              Delete
            </Button>
          </div>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Configuration</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
              <dt className="text-muted-foreground">Environment</dt>
              <dd>{flag.environmentName}</dd>
              <dt className="text-muted-foreground">Strategy</dt>
              <dd>
                <FlagTypeBadge type={flag.type} rolloutPercentage={flag.rolloutPercentage} />
              </dd>
              <dt className="text-muted-foreground">Version</dt>
              <dd className="font-mono">v{flag.version}</dd>
              <dt className="text-muted-foreground">Description</dt>
              <dd>{flag.description || "—"}</dd>
              <dt className="text-muted-foreground">Created</dt>
              <dd>
                {formatDateTime(flag.createdAt)} by {flag.createdByEmail}
              </dd>
              <dt className="text-muted-foreground">Last modified</dt>
              <dd>
                {formatDateTime(flag.updatedAt)} by {flag.updatedByEmail}
              </dd>
            </dl>

            {flag.targetingRules.length > 0 && (
              <div className="space-y-1 border-t border-border pt-3">
                <p className="text-xs font-medium text-muted-foreground">Targeting rules</p>
                <ul className="space-y-1">
                  {flag.targetingRules.map((rule, i) => (
                    <li key={i} className="font-mono text-xs">
                      {rule.attribute} {rule.operator === "EQUALS" ? "=" : "!="} &quot;{rule.value}&quot;
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </CardContent>
        </Card>

        <EvaluationPlayground flagId={flag.id} />
      </div>

      <Tabs defaultValue="audit">
        <TabsList>
          <TabsTrigger value="audit">Audit history</TabsTrigger>
        </TabsList>
        <TabsContent value="audit" className="mt-3">
          <DataTable
            columns={auditColumns}
            data={auditQuery.data?.content ?? []}
            isLoading={auditQuery.isLoading}
            emptyMessage="No changes recorded yet."
            page={auditPage}
            totalPages={auditQuery.data?.totalPages ?? 0}
            onPageChange={setAuditPage}
          />
        </TabsContent>
      </Tabs>

      {isAdmin && <FlagFormDialog open={editOpen} onOpenChange={setEditOpen} mode="edit" flag={flag} />}

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete &quot;{flag.name}&quot;?</AlertDialogTitle>
            <AlertDialogDescription>
              This removes the flag immediately. A DELETE audit entry with its last known configuration is kept
              permanently — this action itself cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              disabled={deleteFlag.isPending}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {deleteFlag.isPending ? <Loader2 className="size-4 animate-spin" /> : null}
              Delete
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
