import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/lib/utils";

export function StatCard({
  label,
  value,
  icon: Icon,
  isLoading,
  tone = "default",
}: {
  label: string;
  value: number | string;
  icon: LucideIcon;
  isLoading?: boolean;
  tone?: "default" | "success" | "warning" | "info" | "violet";
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
        <div
          className={cn(
            "flex size-8 items-center justify-center rounded-md",
            tone === "success" && "bg-success/15 text-success",
            tone === "warning" && "bg-warning/15 text-warning",
            tone === "info" && "bg-info/15 text-info",
            tone === "violet" && "bg-brand-violet/15 text-brand-violet",
            tone === "default" && "bg-primary/10 text-primary",
          )}
        >
          <Icon className="size-4" />
        </div>
      </CardHeader>
      <CardContent>
        {isLoading ? <Skeleton className="h-8 w-16" /> : <div className="text-2xl font-semibold tabular-nums">{value}</div>}
      </CardContent>
    </Card>
  );
}
