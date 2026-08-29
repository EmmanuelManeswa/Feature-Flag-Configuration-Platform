# ADR-001: Deterministic percentage-rollout algorithm

**Status:** Accepted

## Context

`PERCENTAGE_ROLLOUT` flags must let, say, 20% of users see a feature, and the
same user must consistently land on the same side of that line across
requests, restarts, and server instances. The assessment explicitly calls out
the wrong approach: `random() < 0.20` reassigns a coin flip on every call,
so the same user could see the feature toggle on and off between two
requests a second apart. That's unusable for anything with a coherent UX
(a UI variant, a multi-step migration, anything cached client-side).

## Decision

Bucket membership is a pure function of `(flagKey, environmentName, stableIdentifier)`,
with no server-side state:

1. Concatenate `"{flagKey}:{environmentName}:{stableIdentifier}"`.
2. SHA-256 the UTF-8 bytes.
3. Take the first 4 bytes of the digest as an unsigned 32-bit big-endian integer.
4. Reduce modulo 100 → a bucket in `[0, 100)`.
5. The caller is included iff `bucket < rolloutPercentage`.

Implemented in `evaluation.domain.FeatureFlagEvaluator.computeBucket`, a static
method with no dependencies beyond `java.security.MessageDigest`.

## Why this shape

- **Deterministic and stateless.** Nothing is written down anywhere to
  remember who's "in" — the hash *is* the memory. Every instance of the
  backend computes the identical bucket for the identical inputs, so this
  scales horizontally for free and survives restarts/deploys without a
  migration or reconciliation step.
- **Changes only when something meaningful changes.** A user's bucket moves
  only if the flag's key changes, the environment changes, or their own
  stable identifier changes — never because of *when* they asked, and never
  because a second server happened to answer the request.
- **Independent across flags.** Hashing `flagKey` into the input means the
  same user is *not* correlated across different flags — being in the 20%
  bucket for `flag-a` says nothing about their bucket for `flag-b`. Verified
  by `differentFlagKeysProduceIndependentBucketsForTheSameUser` in
  `FeatureFlagEvaluatorTest`.
- **SHA-256 over a cheaper hash.** A non-cryptographic hash (e.g. Murmur3)
  would be faster, and speed does matter on the evaluation hot path — but
  `MessageDigest.getInstance("SHA-256")` is a few hundred nanoseconds per
  call on top of a Redis round-trip that already dominates latency (see
  `docs/caching.md` and `ADR-002`), so the extra safety margin (well-studied
  avalanche/distribution properties, no obscure bias in the low bits the way
  some fast hashes have) was worth it at this scale. If evaluation throughput
  ever became the bottleneck, this is the first place to profile and
  potentially swap.
- **Modulo 100, not a full 32-bit range compared against a fraction.** Buckets
  in `[0, 100)` map directly onto "percentage" as a mental model and let the
  UI show a bucket number a reviewer can sanity-check by eye.

## What was rejected

- **`random()` per request** — explicitly disallowed by the assessment, and
  for good reason: it makes rollout membership unobservable and untestable
  (you can't write a test asserting "user X is always excluded").
- **Consistent hashing / hash ring** — real value for *distributing load
  across shards*, not for *percentage membership decisions*; would add
  complexity (ring maintenance, rebalancing) with no benefit here.
- **Persisting bucket assignment per user in Postgres** — would work, but
  adds a write on first-evaluation, a table to manage, and a reconciliation
  question when the rollout percentage changes. The hash approach gets
  the same consistency guarantee for free.

## Verification

`FeatureFlagEvaluatorTest` includes:
- Golden-vector tests: bucket values cross-checked against an independent
  Python (`hashlib.sha256`) implementation of the same documented algorithm —
  this pins the exact byte-to-bucket mapping, not just Java-internal
  self-consistency.
- Determinism: repeated evaluation of the same context yields the same result.
- Boundary behavior: 0% excludes everyone, 100% includes everyone.
- Approximate uniformity: ~5,000 synthetic users at a 30% rollout land within
  27–33% observed inclusion.
