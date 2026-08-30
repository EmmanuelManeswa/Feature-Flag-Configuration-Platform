import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { AuditLogDto } from "@/types/api";

const STYLES: Record<AuditLogDto["action"], string> = {
  CREATE: "bg-success/15 text-success border-success/20",
  UPDATE: "bg-info/15 text-info border-info/20",
  DELETE: "bg-destructive/15 text-destructive border-destructive/20",
};

export function ActionBadge({ action }: { action: AuditLogDto["action"] }) {
  return (
    <Badge variant="outline" className={cn("font-medium", STYLES[action])}>
      {action}
    </Badge>
  );
}
