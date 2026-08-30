# ADR-002: Caching strategy

**Status:** Accepted

## Context

The evaluation endpoint (`POST /api/v1/flags/{id}/evaluate`) is the one path
in this platform that's meant to be called at real application-traffic
volume — every feature check in a consuming app, potentially per-request.
It needs to be fast, and it needs to survive Redis being unavailable without
taking the whole platform down with it.

## Decision

Cache-aside, Redis-backed, keyed by the flag's UUID:

```
key:   feature-flag:{flagId}
value: JSON-serialized FeatureFlagSnapshot
TTL:   app.cache.flag-ttl-seconds (default 300s)
```

Path: `EvaluationService.evaluate(flagId, context)` asks
`FeatureFlagCache.get(flagId)` first. Hit → skip Postgres entirely, run
`FeatureFlagEvaluator` directly. Miss → load from `FeatureFlagRepository`,
call `FeatureFlagEvaluator`, then write the fresh snapshot into the cache
before returning (so the *next* call for this flag is a hit).

## Why key by flag ID, not `{environment}:{flagKey}`

A commonly suggested pattern for this kind of system is
`feature-flag:{environment}:{flagKey}`. That's the right shape *if* the
evaluation endpoint takes `(environment, key)` as its own request
parameters — which is how a real external SDK would call it, since a
consuming application knows its own environment and the flag's human-
readable key, not an internal database UUID.

This project's evaluate endpoint, per the assessment's own API shape, is
`POST /api/v1/flags/{id}/evaluate` — the admin UI (and the evaluation
playground) already has a specific flag selected from a list, so it always
has the ID on hand. Keying the cache on that ID directly means a cache hit
requires zero Postgres involvement. Keying on `{environment}:{key}` instead
would mean *first* resolving `id → (environment, key)`, which is itself a
Postgres read — on every single evaluation, hit or miss, which defeats the
entire purpose of caching on this endpoint shape.

(If a separate SDK-facing endpoint that accepts `environment`/`key` directly
as parameters were added later, it could use the `{environment}:{key}`
key format for its own cache lookups without conflicting with this one —
Redis keys are just strings, nothing stops both schemes coexisting.)

## Write path: write-through, not just invalidate-and-let-it-repopulate

`FeatureFlagService.create/update/delete` all call `cache.put(...)` (or
`cache.evict(...)` for delete) in the same request that commits the
Postgres write, immediately after the transaction succeeds. This means the
very next evaluation after a mutation reads the *new* value from cache
directly — there's no window where the first post-mutation evaluation has
to eat a cache miss and a Postgres round-trip. The TTL still exists as a
safety net (in case a `put`/`evict` call is itself lost — see failure
handling below) but is not what makes evaluations see fresh data promptly.

## Consistency model

This is **not** strongly consistent, and no claim is made that it is. There
is a real (if narrow) window: between Postgres commit and the `cache.put`/
`cache.evict` call completing, a concurrent evaluation could still read a
stale cached value. In practice that window is milliseconds within the same
request thread, not a separate async job — but it exists.

If the `put`/`evict` call itself fails (Redis down at that exact moment),
the cache can hold a stale entry for up to `flag-ttl-seconds` (5 minutes by
default) before it naturally expires and the next evaluation reads fresh
from Postgres. For a platform managing feature rollouts, a few minutes of
bounded staleness on a *cache write failure* (an already-degraded state) is
an acceptable trade-off against the alternative of making every evaluation
synchronously block on Redis being healthy — see "Redis failure handling"
below.

## Redis failure handling

Every method on `RedisFeatureFlagCache` is wrapped in try/catch:
- `get` failure → treated identically to a cache miss (falls through to Postgres).
- `put`/`evict` failure → logged as a warning and swallowed; the mutation
  itself has already committed to Postgres successfully, so the flag change
  is not lost — only its cache reflection is delayed until TTL expiry.

`FeatureFlagCache` is an interface for exactly this reason: business logic
(`EvaluationService`, `FeatureFlagService`) depends on it, never on
`StringRedisTemplate` directly, so this failure-handling lives in one place
and every caller gets the same "Redis is a performance layer, not a
dependency the request can fail on" guarantee automatically. Postgres is
the system of record; Redis is disposable and the platform is designed to
behave correctly (if slower) with it turned off entirely.

## Serialization

`FeatureFlagSnapshot` (a record) round-trips through Jackson as plain JSON
text via a `StringRedisTemplate` — no custom `RedisSerializer`, no binary
format. Chosen for the same reason as the JDBC JSON mapping on the entity:
one JSON library already on the classpath, human-readable values if you
`redis-cli GET` a key during a demo, no extra dependency to justify.

## What was rejected

- **Write-behind / async cache population** — real value at much higher
  write volume than this project has; here it just adds a queue and a
  consistency question ("did the write actually make it to Redis yet?")
  with no corresponding benefit.
- **Caching the whole `feature_flags` table client-side / in-process** —
  would eliminate the Redis round-trip entirely, but reintroduces the
  original problem (every instance needs its own invalidation signal on
  every mutation, which is exactly what Redis-as-shared-cache avoids) and
  is overkill for this project's scale.
