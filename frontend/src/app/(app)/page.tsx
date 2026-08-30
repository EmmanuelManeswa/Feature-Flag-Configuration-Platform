"use client";

import Link from "next/link";
import { CheckCircle2, Flag, Layers, Percent, XCircle } from "lucide-react";
import { StatCard } from "@/components/dashboard/stat-card";
import { ActionBadge } from "@/components/audit/action-badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Button } from "@/components/ui/button";
import { useFlags } from "@/features/flags/hooks";
import { useEnvironments } from "@/features/environments/hooks";
import { useAuditLogs } from "@/features/audit/hooks";
import { formatRelativeTime } from "@/lib/format";

export default function DashboardPage() {
  // Lean scope: no dedicated /dashboard/stats endpoint — aggregated
  // client-side from a single generously-sized page of flags. Fine for this
  // project's demo data volume; a real deployment with thousands of flags
  // would want a backend aggregation endpoint instead (see docs/production-readiness.md).
  const flagsQuery = useFlags({ page: 0, size: 200 });
  const environmentsQuery = useEnvironments();
  const recentAuditQuery = useAuditLogs({ page: 0, size: 6 });

  const flags = flagsQuery.data?.content ?? [];
  const enabledCount = flags.filter((f) => f.enabled).length;
  const disabledCount = flags.length - enabledCount;
  const rolloutCount = flags.filter((f) => f.type === "PERCENTAGE_ROLLOUT").length;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="text-sm text-muted-foreground">Overview of feature flags, environments, and recent activity.</p>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <StatCard label="Total Flags" value={flagsQuery.data?.totalElements ?? 0} icon={Flag} isLoading={flagsQuery.isLoading} />
        <StatCard label="Enabled" value={enabledCount} icon={CheckCircle2} tone="success" isLoading={flagsQuery.isLoading} />
        <StatCard label="Disabled" value={disabledCount} icon={XCircle} isLoading={flagsQuery.isLoading} />
        <StatCard label="Rollouts" value={rolloutCount} icon={Percent} tone="info" isLoading={flagsQuery.isLoading} />
        <StatCard
          label="Environments"
          value={environmentsQuery.data?.length ?? 0}
          icon={Layers}
          tone="violet"
          isLoading={environmentsQuery.isLoading}
        />
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle className="text-base">Recent activity</CardTitle>
          <Button variant="ghost" size="sm" asChild>
            <Link href="/audit">View all</Link>
          </Button>
        </CardHeader>
        <CardContent>
          {recentAuditQuery.isLoading ? (
            <div className="space-y-3">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-10 w-full" />
              ))}
            </div>
          ) : recentAuditQuery.data?.content.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">
              No configuration changes yet. Create a feature flag to get started.
            </p>
          ) : (
            <ul className="divide-y divide-border">
              {recentAuditQuery.data?.content.map((entry) => (
                <li key={entry.id} className="flex items-center justify-between gap-4 py-2.5 text-sm">
                  <div className="flex min-w-0 items-center gap-3">
                    <ActionBadge action={entry.action} />
                    <span className="truncate text-muted-foreground">
                      <span className="font-medium text-foreground">{entry.actorEmail}</span> {entry.entityType.toLowerCase()}
                    </span>
                  </div>
                  <span className="shrink-0 text-xs text-muted-foreground">{formatRelativeTime(entry.createdAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
