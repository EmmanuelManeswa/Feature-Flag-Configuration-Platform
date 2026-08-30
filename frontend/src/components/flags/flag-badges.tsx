import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { FlagType } from "@/types/api";

export function FlagStatusBadge({ enabled }: { enabled: boolean }) {
  return (
    <Badge
      variant="outline"
      className={cn(
        "gap-1.5 font-medium",
        enabled ? "border-success/20 bg-success/15 text-success" : "border-border bg-muted text-muted-foreground",
      )}
    >
      <span className={cn("size-1.5 rounded-full", enabled ? "bg-success" : "bg-muted-foreground")} />
      {enabled ? "Enabled" : "Disabled"}
    </Badge>
  );
}

export function FlagTypeBadge({ type, rolloutPercentage }: { type: FlagType; rolloutPercentage?: number | null }) {
  if (type === "PERCENTAGE_ROLLOUT") {
    return (
      <Badge variant="outline" className="border-info/20 bg-info/15 font-mono text-xs font-medium text-info">
        {rolloutPercentage}% rollout
      </Badge>
    );
  }
  return (
    <Badge variant="outline" className="text-xs font-medium">
      Boolean
    </Badge>
  );
}
