"use client";

import { useState } from "react";
import { Layers, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { EnvironmentFormDialog } from "@/components/environments/environment-form-dialog";
import { useEnvironments } from "@/features/environments/hooks";
import { useAuth } from "@/providers/auth-provider";
import { formatDateTime } from "@/lib/format";

export default function EnvironmentsPage() {
  const { user } = useAuth();
  const { data: environments, isLoading } = useEnvironments();
  const [createOpen, setCreateOpen] = useState(false);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Environments</h1>
          <p className="text-sm text-muted-foreground">Groupings that scope feature flags (e.g. DEV, STAGING, PROD).</p>
        </div>
        {user?.role === "ADMIN" && (
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="size-4" />
            New environment
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-28 w-full" />
          ))}
        </div>
      ) : environments?.length === 0 ? (
        <p className="py-10 text-center text-sm text-muted-foreground">No environments yet.</p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {environments?.map((env) => (
            <Card key={env.id}>
              <CardHeader className="flex flex-row items-center gap-2 space-y-0">
                <div className="flex size-8 items-center justify-center rounded-md bg-primary/10 text-primary">
                  <Layers className="size-4" />
                </div>
                <CardTitle className="font-mono text-sm">{env.name}</CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-sm text-muted-foreground">{env.description || "No description."}</p>
                <p className="mt-2 text-xs text-muted-foreground">Created {formatDateTime(env.createdAt)}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <EnvironmentFormDialog open={createOpen} onOpenChange={setCreateOpen} />
    </div>
  );
}
