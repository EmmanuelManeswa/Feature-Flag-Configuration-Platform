import { getStoredToken } from "./auth-storage";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const RECONNECT_DELAY_MS = 3000;

export type SseConnectionStatus = "connecting" | "open" | "closed";

interface ParsedSseEvent {
  eventName: string;
  data: string;
}

/**
 * Parses one `\n\n`-delimited SSE event block into its event name and data.
 * A pure function, exported for direct unit testing rather than only
 * reachable through a full fetch+ReadableStream round trip. Returns null
 * for a comment-only block (a heartbeat — see FlagChangeNotifier.heartbeat
 * on the backend), since there's nothing for a subscriber to act on.
 */
export function parseSseEvent(rawEvent: string): ParsedSseEvent | null {
  let eventName = "message";
  const dataLines: string[] = [];

  for (const line of rawEvent.split("\n")) {
    if (line === "" || line.startsWith(":")) continue;
    if (line.startsWith("event:")) {
      eventName = line.slice("event:".length).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trim());
    }
  }

  if (dataLines.length === 0) return null;
  return { eventName, data: dataLines.join("\n") };
}

interface SubscribeOptions {
  onEvent: (eventName: string, data: string) => void;
  onStatusChange?: (status: SseConnectionStatus) => void;
}

/**
 * A hand-rolled SSE client using `fetch` + a streamed `ReadableStream`
 * instead of the native `EventSource`. `EventSource` cannot set request
 * headers, so authenticating it would mean passing the JWT as a URL query
 * parameter — which leaks into server access logs and browser history, a
 * real secret-exposure risk this project's non-negotiable rules explicitly
 * rule out. This reuses the same bearer-token header every other API call
 * uses instead.
 *
 * Reconnects automatically (fixed 3s delay — no need for backoff/jitter at
 * this scale: one backend, one browser tab, not a fleet of clients that
 * could thundering-herd it) on any error or unexpected stream close. Call
 * the returned function to stop permanently (e.g. on component unmount).
 */
export function subscribeToEventStream(path: string, { onEvent, onStatusChange }: SubscribeOptions): () => void {
  const controller = new AbortController();
  let stopped = false;

  async function connect() {
    if (stopped) return;
    onStatusChange?.("connecting");

    try {
      const token = getStoredToken();
      const response = await fetch(new URL(path, API_BASE_URL), {
        headers: {
          Accept: "text/event-stream",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        signal: controller.signal,
      });

      if (!response.ok || !response.body) {
        throw new Error(`SSE connection failed with status ${response.status}`);
      }
      onStatusChange?.("open");

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (!stopped) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        let separatorIndex = buffer.indexOf("\n\n");
        while (separatorIndex !== -1) {
          const rawEvent = buffer.slice(0, separatorIndex);
          buffer = buffer.slice(separatorIndex + 2);
          const parsed = parseSseEvent(rawEvent);
          if (parsed) onEvent(parsed.eventName, parsed.data);
          separatorIndex = buffer.indexOf("\n\n");
        }
      }
    } catch {
      // Network error, aborted fetch, or the server closing the connection —
      // all handled the same way: fall through to the reconnect below. The
      // specific cause isn't actionable by the caller; a live-updates
      // indicator only needs "connected" vs "not right now".
    }

    if (stopped) return;
    onStatusChange?.("closed");
    setTimeout(connect, RECONNECT_DELAY_MS);
  }

  connect();

  return () => {
    stopped = true;
    controller.abort();
  };
}
