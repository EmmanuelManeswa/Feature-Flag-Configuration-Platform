/**
 * FeatureFlagClient — a minimal, dependency-free SDK for the Feature Flag &
 * Configuration Platform's evaluation API.
 *
 * This is a *sample* of the shape a real application's flag client would
 * take, not a published package: plain Node.js (>=18, for native fetch),
 * zero npm dependencies. An application team would typically wrap this in
 * their own thin adapter (e.g. a React hook, a Spring bean) rather than
 * call it directly from request-handling code, but the core contract —
 * authenticate once, evaluate flags by ID with a stable identifier — stays
 * the same regardless of what's on top of it.
 *
 * Deliberately does NOT cache evaluation results client-side: the backend
 * already does that (Redis, cache-aside, see ADR-002) and is the source of
 * truth for "is this flag currently on". A real integration would call
 * `evaluate()` on the request path (or at process start for a long-lived
 * worker) rather than reimplementing caching here.
 */
export class FeatureFlagClient {
  #baseUrl;
  #accessToken = null;

  constructor(baseUrl = "http://localhost:8080") {
    this.#baseUrl = baseUrl.replace(/\/$/, "");
  }

  /** Authenticates once; the returned token is reused by every other call. */
  async login(email, password) {
    const response = await this.#request("POST", "/api/v1/auth/login", { email, password }, { auth: false });
    this.#accessToken = response.accessToken;
    return response.user;
  }

  /** Lists flags, optionally scoped to one environment. */
  async listFlags({ environmentId, page = 0, size = 20 } = {}) {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (environmentId) params.set("environmentId", environmentId);
    return this.#request("GET", `/api/v1/flags?${params}`);
  }

  async getFlag(flagId) {
    return this.#request("GET", `/api/v1/flags/${flagId}`);
  }

  /**
   * The one call an application actually needs on its request path:
   * "should this feature be on for this user, right now". `stableIdentifier`
   * must be something durable for the same user across requests (a user ID,
   * not a session ID) — percentage rollouts are a deterministic hash of
   * `flagKey:environment:stableIdentifier`, so the same identifier always
   * lands on the same side of the rollout line (see ADR-001). A random or
   * per-request identifier would make a "50% rollout" flicker on and off
   * for the same user from one request to the next.
   */
  async evaluate(flagId, { stableIdentifier, attributes = {} }) {
    return this.#request("POST", `/api/v1/flags/${flagId}/evaluate`, { stableIdentifier, attributes });
  }

  /** Basic operational metrics for one flag (see the stretch-goal endpoint). */
  async getMetrics(flagId) {
    return this.#request("GET", `/api/v1/flags/${flagId}/metrics`);
  }

  /**
   * Subscribes to the live flag-change stream (Server-Sent Events).
   * `onEvent(eventName, data)` fires for every named event ("connected",
   * "flag-change"); heartbeat comments are consumed internally and never
   * reach the callback. Returns an unsubscribe function.
   */
  async streamChanges(onEvent) {
    const response = await fetch(`${this.#baseUrl}/api/v1/flags/stream`, {
      headers: { Accept: "text/event-stream", ...this.#authHeader() },
    });
    if (!response.ok || !response.body) {
      throw new Error(`Failed to open flag-change stream: HTTP ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let stopped = false;

    (async () => {
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
    })();

    return () => {
      stopped = true;
      reader.cancel().catch(() => {});
    };
  }

  #authHeader() {
    return this.#accessToken ? { Authorization: `Bearer ${this.#accessToken}` } : {};
  }

  async #request(method, path, body, { auth = true } = {}) {
    const response = await fetch(`${this.#baseUrl}${path}`, {
      method,
      headers: {
        ...(body ? { "Content-Type": "application/json" } : {}),
        ...(auth ? this.#authHeader() : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });

    if (response.status === 204) return undefined;

    const contentType = response.headers.get("content-type") ?? "";
    const payload = contentType.includes("json") ? await response.json() : undefined;

    if (!response.ok) {
      // Mirrors the RFC 7807 Problem Details shape the backend always
      // returns for errors — see GlobalExceptionHandler.
      const detail = payload?.detail ?? `Request failed with status ${response.status}`;
      const error = new Error(detail);
      error.status = response.status;
      error.problem = payload;
      throw error;
    }

    return payload;
  }
}

function parseSseEvent(rawEvent) {
  let eventName = "message";
  const dataLines = [];
  for (const line of rawEvent.split("\n")) {
    if (line === "" || line.startsWith(":")) continue;
    if (line.startsWith("event:")) eventName = line.slice("event:".length).trim();
    else if (line.startsWith("data:")) dataLines.push(line.slice("data:".length).trim());
  }
  return dataLines.length === 0 ? null : { eventName, data: dataLines.join("\n") };
}
