"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { ApiError } from "@/lib/api-client";

/**
 * staleTime of 30s: this is an internal admin tool with a handful of
 * concurrent users, not a public high-traffic app — data doesn't need to be
 * instantly fresh on every refocus, but shouldn't feel stale either.
 * Mutations always explicitly invalidate the queries they affect (see each
 * feature's hooks), so staleTime is a background-refresh tuning knob, not
 * the only thing keeping the UI correct.
 *
 * Retries skip 4xx entirely (retrying a 400/403/404/409 just repeats the
 * same failure) and cap at 2 attempts for everything else — enough to ride
 * out a blip without masking a genuinely down backend behind a long spinner.
 */
function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
    return false;
  }
  return failureCount < 2;
}

export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            gcTime: 5 * 60_000,
            retry: shouldRetry,
            refetchOnWindowFocus: false,
          },
          mutations: {
            retry: false,
          },
        },
      }),
  );

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
