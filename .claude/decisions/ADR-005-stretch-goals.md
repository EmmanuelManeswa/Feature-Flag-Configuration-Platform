# ADR-005: Stretch goals — live updates, evaluation metrics, sample SDK client

**Status:** Accepted

## Context

The assessment lists three optional stretch goals: real-time flag-change updates
(SSE/WebSocket), an SDK-style sample client for the evaluation API, and basic evaluation
metrics (count per flag/result). All three were implemented after the core assessment
was otherwise complete, so each decision below is scoped against a platform that
already had a working evaluation engine, cache, and audit trail — nothing here changes
those.

## Live updates: Server-Sent Events, not WebSocket

**Decision:** `GET /api/v1/flags/stream` — one-way, server-to-client SSE.

The traffic this feature actually needs to carry is one-way: "a flag changed, go
refetch." Nothing the client does in response needs to go back over the same
connection — the frontend already has a full HTTP API (create/update/delete/evaluate)
for every write it needs to make. A WebSocket's bidirectional channel would be unused
capability for this use case, at the cost of a more complex protocol (its own framing,
ping/pong keep-alive semantics, a different Spring abstraction) for zero corresponding
benefit here. SSE is the narrower tool that exactly fits the actual shape of the
requirement, and Spring MVC supports it natively (`SseEmitter`) with no extra dependency.

**Delivery mechanism:** `FeatureFlagService.create/update/delete` each publish a
`FlagChangeEvent` (a plain record, not tied to Spring) via `ApplicationEventPublisher`,
from inside their own `@Transactional` method. `FlagChangeNotifier` listens with
`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` — deliberately not
the default synchronous phase — so a subscriber only ever hears about a change that
actually committed. A mutation that rolls back (a validation failure after the initial
checks, a database constraint violation) never reaches a subscriber as a change,
because the event fires after commit, and a rolled-back transaction never commits.

**Subscriber state:** an in-memory `CopyOnWriteArrayList<SseEmitter>` on
`FlagChangeNotifier`. Correct and sufficient for this project's deployment target (a
single backend instance), and it's what keeps the feature at zero extra infrastructure
dependency — no message broker to run, configure, or explain in a demo. The explicit,
accepted limitation: this does not fan out across multiple backend instances behind a
load balancer. A subscriber connected to instance A never hears about a change
committed on instance B. See "What a multi-instance deployment would need" below.

**Authentication over SSE:** the frontend's `subscribeToEventStream` (and the SDK
client's `streamChanges`) use `fetch` + a streamed `ReadableStream`, not the browser's
native `EventSource`. `EventSource` cannot set request headers, so authenticating it
the usual way means putting the JWT in the URL as a query parameter — which leaks into
server access logs and browser history. That's a real secret-exposure risk this
project's own non-negotiable rules explicitly forbid ("never expose secrets... in
logs"), so the extra ~40 lines to hand-parse SSE's `event:`/`data:`/comment wire format
over a normal authenticated fetch call were worth it over the native API's convenience.

**Heartbeat:** `FlagChangeNotifier.heartbeat()`, a `@Scheduled(fixedRate = 15_000)`
method, sends an SSE comment line (not a data event — nothing for a subscriber to
parse) to every open connection. Without it, a corporate proxy or load balancer with an
idle-connection timeout shorter than the time between real flag changes would silently
close the stream, and the client wouldn't know until its next attempted read failed.

## Evaluation metrics: read Micrometer back, don't add a second write path

**Decision:** `GET /api/v1/flags/{id}/metrics` reads back the same
`feature_flag.evaluations{flag,result}` Micrometer counters `EvaluationService.evaluate`
already increments on every call, filtered to one flag's key and grouped by the
`result` tag, via `MeterRegistry.find(...)`.

The alternative — a dedicated `evaluation_events` table, one row per evaluation, with
its own write path and its own query — would be the right design for a system that
needs durable, queryable evaluation history (which user saw which result, when,
correlated with other events). That's a meaningfully bigger feature than "basic
evaluation metrics such as count per flag/result," and this project already emits
exactly the counters the stretch goal asks for; the only gap was that they were only
visible in Prometheus's raw text-exposition format at `/actuator/prometheus`, mixed in
with every other metric the JVM and Spring Boot expose. Shaping and scoping the
existing signal, rather than duplicating it, was the smaller and more honest way to
satisfy the requirement.

**Accepted limitation:** counts live in the Micrometer registry's in-memory counters,
so they reset to zero on backend restart. This is explicitly a basic *operational*
signal ("is this flag getting traffic, roughly what split"), not a durable analytics
requirement — the same distinction Micrometer itself draws by design (it's built for
live dashboards, not historical queries).

## Sample SDK client: plain Node, zero dependencies, not a published package

**Decision:** `examples/sdk-client/client.mjs` and `example.mjs` — plain Node.js
(`>=18`, for native `fetch`), no `package.json` dependencies, not published anywhere.

The purpose of this stretch goal, read literally, is showing *how an application would
consume the evaluation API* — the shape of the integration, not a production-grade,
versioned, publishable SDK with retries, backoff, connection pooling, or its own test
suite. A minimal client that's readable start to finish in a couple of minutes
communicates that shape more clearly than a heavier one would, and matches this
project's own "lean & rubric-optimized" scope calibration (see `CLAUDE.md`) rather than
over-building a stretch goal at the expense of the assessment's core requirements.

`example.mjs` deliberately evaluates a real `PERCENTAGE_ROLLOUT` flag for several
different `stableIdentifier`s and prints the result twice across separate runs — this
was chosen specifically because it's a *visible, external* demonstration that
[ADR-001](ADR-001-evaluation-algorithm.md)'s deterministic-hash decision holds from
outside the codebase, not just in a unit test that already knows the algorithm.

## What a multi-instance deployment would need (not built, out of scope here)

If this platform ran behind a load balancer with more than one backend instance:

- **Live updates** would need a shared broker so every instance's SSE subscribers hear
  about a change committed on any instance — Redis Pub/Sub is the natural next step
  (already a dependency here for caching), published from the same
  `AFTER_COMMIT` point `FlagChangeNotifier` already uses, just broadcast to Redis
  instead of (or in addition to) the local subscriber list.
- **Evaluation metrics** would need each instance's counters aggregated, not read from
  a single instance's in-memory registry — Prometheus's own scrape-and-aggregate model
  already does this correctly today for the raw counters at `/actuator/prometheus`
  across instances; only this project's *per-flag friendly endpoint* is single-instance
  scoped, since it reads one process's local `MeterRegistry` directly.

Both are documented here rather than built, because this project's deployment target
(see `docs/production-readiness.md`) is explicitly single-instance, and building
multi-instance fan-out for a single-instance target would be exactly the kind of
unnecessary complexity `CLAUDE.md` warns against ("don't design for hypothetical future
requirements").
