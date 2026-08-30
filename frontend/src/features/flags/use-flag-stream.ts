"use client";

import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { subscribeToEventStream, type SseConnectionStatus } from "@/lib/sse-client";
import { flagKeys } from "./hooks";

/**
 * Subscribes to the backend's SSE flag-change stream and invalidates the
 * flags query cache on every event, so the flags list / dashboard stay live
 * without polling. Returns the connection status for a small indicator —
 * see LiveUpdatesIndicator, the single place this hook is mounted (mounting
 * it more than once would open redundant SSE connections for no benefit).
 */
export function useFlagChangeStream(enabled: boolean): SseConnectionStatus {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<SseConnectionStatus>("connecting");

  useEffect(() => {
    if (!enabled) return;

    const unsubscribe = subscribeToEventStream("/api/v1/flags/stream", {
      onStatusChange: setStatus,
      onEvent: (eventName) => {
        if (eventName === "flag-change") {
          queryClient.invalidateQueries({ queryKey: flagKeys.all });
        }
      },
    });

    return unsubscribe;
  }, [enabled, queryClient]);

  return enabled ? status : "closed";
}
