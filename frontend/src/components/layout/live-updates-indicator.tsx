"use client";

import { useFlagChangeStream } from "@/features/flags/use-flag-stream";
import { useAuth } from "@/providers/auth-provider";
import { cn } from "@/lib/utils";

/**
 * A small connection-status dot for the SSE flag-change stream. The one
 * place {@link useFlagChangeStream} is mounted — it both drives the query
 * cache invalidation and surfaces status here, so nothing else needs to
 * (re)subscribe.
 */
export function LiveUpdatesIndicator() {
  const { user } = useAuth();
  const status = useFlagChangeStream(Boolean(user));

  const label = status === "open" ? "Live" : status === "connecting" ? "Connecting…" : "Reconnecting…";

  return (
    <div
      className="hidden items-center gap-1.5 rounded-full border border-border px-2.5 py-1 text-xs text-muted-foreground sm:flex"
      title="Real-time flag-change updates (Server-Sent Events)"
    >
      <span
        className={cn(
          "size-1.5 rounded-full",
          status === "open" && "bg-success",
          status === "connecting" && "animate-pulse bg-warning",
          status === "closed" && "bg-destructive",
        )}
      />
      {label}
    </div>
  );
}
