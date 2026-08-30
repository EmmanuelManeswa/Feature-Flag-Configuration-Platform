# Sample SDK client

A minimal, dependency-free Node.js client for the platform's evaluation API — the
"stretch goal" sample client showing how an application would actually consume this
service, as opposed to the Swagger UI a developer uses to explore it.

`client.mjs` is plain Node (>=18, for native `fetch`), zero npm dependencies, not a
published package. It's small enough to read end to end in a couple of minutes — that's
deliberate: the point is to show the *shape* of a real integration (authenticate once,
evaluate by flag ID with a stable identifier, treat the response as authoritative), not
to ship a polished SDK with retries/backoff/telemetry.

## What it demonstrates

- `login()` — authenticate once, reuse the token for every subsequent call.
- `evaluate(flagId, { stableIdentifier, attributes })` — the one call an application
  actually needs on its request path. Run against a `PERCENTAGE_ROLLOUT` flag for
  several different `stableIdentifier`s, `example.mjs` prints the same true/false
  pattern on every run — proof the rollout is a deterministic hash
  (`flagKey:environment:stableIdentifier`, see
  [ADR-001](../../.claude/decisions/ADR-001-evaluation-algorithm.md)), not `Math.random()`.
  A real user's flag state never flickers between requests.
- `getMetrics(flagId)` — the basic evaluation-metrics stretch goal, from the same client.
- `streamChanges(onEvent)` — subscribes to the live SSE flag-change stream and prints
  every event for 5 seconds, so you can edit a flag in another terminal (or the running
  app) and watch the event arrive here in real time.

## Running it

```bash
# 1. Have the backend running (from the repo root):
cd backend && set -a && source ../.env && set +a && ./mvnw spring-boot:run
# — or —
docker compose up -d

# 2. In another terminal:
cd examples/sdk-client
node example.mjs
```

Point it at a different host with `FFP_API_URL`:

```bash
FFP_API_URL=http://localhost:8080 node example.mjs
```

## Not included (on purpose)

- **Client-side caching of evaluation results.** The backend already caches (Redis,
  cache-aside — see [ADR-002](../../.claude/decisions/ADR-002-caching-strategy.md)) and
  is the source of truth for "is this flag on right now". A real integration calls
  `evaluate()` on the request path rather than reimplementing that caching client-side.
- **Retries, circuit breaking, connection pooling.** Real production SDK concerns, but
  orthogonal to demonstrating the API contract, which is this sample's actual purpose.
- **TypeScript types / npm packaging.** The frontend app (`frontend/src/types/api.ts`,
  `frontend/src/features/flags/api.ts`) already shows the fully-typed version of this
  same contract; duplicating that here would be redundant, not illustrative.
