import { Activity } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useFlagMetrics } from "@/features/flags/hooks";

export function FlagMetricsCard({ flagId }: { flagId: string }) {
  const metricsQuery = useFlagMetrics(flagId);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Activity className="size-4 text-muted-foreground" />
          Evaluation metrics
        </CardTitle>
      </CardHeader>
      <CardContent>
        {metricsQuery.isLoading ? (
          <Skeleton className="h-16 w-full" />
        ) : metricsQuery.isError ? (
          <p className="text-sm text-muted-foreground">Unable to load evaluation metrics.</p>
        ) : (
          <div className="space-y-3">
            <div>
              <p className="text-2xl font-semibold tabular-nums">{metricsQuery.data?.totalEvaluations ?? 0}</p>
              <p className="text-xs text-muted-foreground">
                total evaluations since the backend last restarted (in-memory counters — see README)
              </p>
            </div>
            {Object.entries(metricsQuery.data?.countsByResult ?? {}).length > 0 && (
              <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 border-t border-border pt-3 text-sm">
                {Object.entries(metricsQuery.data?.countsByResult ?? {}).map(([result, count]) => (
                  <div key={result} className="contents">
                    <dt className="font-mono text-muted-foreground">{result}</dt>
                    <dd className="tabular-nums">{count}</dd>
                  </div>
                ))}
              </dl>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
