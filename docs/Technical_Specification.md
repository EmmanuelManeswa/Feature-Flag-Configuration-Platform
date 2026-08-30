---
title: "Feature Flag & Configuration Platform — Technical Specification"
author: "Emmanuel Maneswa"
date: "2026-08-31"
---

# 1. Executive Summary

The Feature Flag & Configuration Platform is a small control plane that lets an engineering
team turn features on or off, and roll them out gradually to a percentage of users, per
environment (DEV/STAGING/PROD), without redeploying the application that reads the flag. It was
built as a senior software developer take-home assessment (Project 1 of a five-project
assessment set) and is scoped, deliberately, as a **lean, rubric-optimized vertical slice**
rather than a maximal feature set: the assessment's own guidance is explicit that "a large but
fragile implementation" scores worse than a focused one, and that principle drove every scope
decision documented in this specification.

Four properties were treated as non-negotiable throughout, because they are the properties that
actually make a feature-flag platform trustworthy in production use, independent of scope:

1. **Rollout membership is deterministic.** The same user is always in or always out of a
   percentage rollout — never a coin flip that could differ between two requests from the same
   person.
2. **Every configuration change is audited and immutable.** No code path exists to delete or
   modify an audit row, enforced at the type level, not just by convention.
3. **Concurrent edits never silently overwrite each other.** Optimistic concurrency with an
   explicit, structured conflict response.
4. **The AI assistant proposes; it never applies.** Every AI-authored suggestion passes through
   the identical validation pipeline as a human-submitted change, and a human must explicitly
   confirm before anything is persisted.

The result is a working, end-to-end system — Spring Boot backend, Next.js frontend, PostgreSQL
system of record, Redis performance cache, and a genuinely functional AI rule assistant with two
interchangeable providers — covered by 58 backend tests and 19 frontend tests, all three of the
assessment's optional stretch goals implemented and live-verified, and documentation (this
specification, the README, five Architecture Decision Records, and a security/production-
readiness review) written to the same bar as the code.

# 2. Problem Statement

Shipping a new feature behind a flag that requires a full redeploy to toggle is slow and risky:
the only way to react to a bad rollout is another deploy, and the only way to test in production
safely is a coordinated release window. The problem this platform solves is giving a team a
small, purpose-built control plane instead — create a flag, target it at an environment, turn it
fully on or roll it out to a percentage of users, exclude a defined subgroup, watch exactly who
changed what and when, and let a reviewer plug in a set of user attributes and see precisely why
a flag resolved the way it did, before the application code that reads the flag ever ships.

# 3. Scope & Approach

## 3.1 Authoritative requirements source

The candidate was given two inputs: the assessment PDF itself
(`docs/Senior_Software_Developer_Take_Home_Assessment.pdf`), and an earlier, much larger
ChatGPT-drafted "mega-prompt" the candidate had prepared independently, specifying an extremely
broad technology surface. The assessment PDF was treated as the authoritative requirements
source throughout — the ChatGPT draft was used only as a menu of technology ideas, filtered hard
against the PDF's own explicit guidance to build "only the features required for a convincing
working vertical slice."

## 3.2 The "lean & rubric-optimized" scope calibration

Recorded as a standing project instruction (`.claude/CLAUDE.md`) and made explicit at the start
of implementation: prefer fewer, well-tested, well-explained things over maximal library
coverage. Concretely, this meant:

- On the frontend: TanStack Query, TanStack Table, and TanStack Form + Zod, each chosen because
  it earns its complexity budget for this project's actual needs (server-state caching, a
  sortable/paginated table, and validated forms respectively) — and explicitly *not* TanStack
  Pacer, Charts, Markdown, or an AI-chat library, none of which had a genuine job to do at this
  scope.
- On the backend: a framework-free evaluation engine (see §5.2) as the one piece of code held to
  the highest engineering bar in the project, because it is the highest-risk business logic —
  every feature check in every consuming application ultimately depends on it being correct.
- In documentation: a small, purpose-built `.claude/` folder (a persistent instructions file, a
  running project-state file, and Architecture Decision Records for decisions that are actually
  load-bearing) rather than exhaustive session-notes ceremony.

## 3.3 What "done" means for this submission

Every item in the assessment's mandatory-deliverables list is implemented, tested where testable,
and documented — see `docs/assessment-compliance.md` for the full requirement-by-requirement
checklist, which is the authoritative compliance record this specification summarizes rather than
duplicates in full.

# 4. Architecture

## 4.1 System overview

```
                         ┌─────────────────────────────────────────┐
                         │              Browser                     │
                         │   Next.js App Router (client-rendered)   │
                         └───────────────┬───────────────────────────┘
                                          │ HTTPS / REST + JWT (Bearer)
                                          │ + SSE (live flag-change stream)
                                          ▼
                         ┌─────────────────────────────────────────┐
                         │            Spring Boot API                │
                         │                                            │
                         │  auth          featureflag                │
                         │  (JWT / roles) (CRUD + optimistic lock)   │
                         │                                            │
                         │  evaluation    audit                      │
                         │  (framework-   (append-only trail)        │
                         │   free core)                              │
                         │                                            │
                         │  ai            common                     │
                         │  (provider     (errors, correlation ID,   │
                         │   abstraction)  security config)          │
                         └───────┬──────────────────┬─────────────────┘
                                 │                  │
                    cache-aside, │                  │ fallback on
                    keyed by     │                  │ miss/failure
                    flag ID      ▼                  ▼
                         ┌──────────────┐   ┌──────────────────┐
                         │    Redis      │   │    PostgreSQL      │
                         │ evaluation    │   │  system of record  │
                         │    cache      │   │                    │
                         └──────────────┘   └──────────────────┘

                         ┌─────────────────────────────────────────┐
                         │     Docker Model Runner (optional)        │
                         │   local LLM, OpenAI-compatible API        │
                         └─────────────────────────────────────────┘
                                 ▲
                                 │ mock (default) or real local model
                                 │
                              ai module
```

## 4.2 Backend package structure (package-by-feature)

The backend is organized **by feature, not by layer** — `featureflag/`, `environment/`,
`audit/`, `evaluation/`, `ai/`, `auth/`, `common/` — so that everything relevant to one domain
concept lives together, rather than splitting `controller/`, `service/`, `repository/` as
top-level packages the way a layer-first structure would. Within each feature package, the
conventional layering still holds: controllers depend on services, services depend on
repositories, and JPA entities are never returned directly over the API — every response is an
explicit Java record DTO.

```
com.featureflagplatform/
├── ai/                    provider abstraction, prompt orchestration, validation pipeline
│   ├── config/  controller/  domain/  dto/  provider/  service/
├── audit/                 append-only audit trail
│   ├── controller/  domain/  dto/  repository/  service/
├── auth/                   JWT issuance/validation, users, roles
│   ├── controller/  domain/  dto/  repository/  security/  service/
├── common/                 cross-cutting: errors, correlation IDs, security config
│   ├── config/  exception/  observability/  security/
├── environment/            DEV/STAGING/PROD-style groupings
│   ├── controller/  domain/  dto/  repository/  service/
├── evaluation/              the framework-free evaluation core + its Spring-facing service
│   ├── domain/  dto/  service/
└── featureflag/             the flag entity, CRUD, and the SSE change-notification pipeline
    ├── controller/  domain/  dto/  event/  mapper/  repository/  service/
```

## 4.3 The evaluation engine: deliberately framework-free

`evaluation.domain` — `EvaluationContext`, `FeatureFlagSnapshot`, `TargetingRule`,
`FeatureFlagEvaluator`, `EvaluationResult`, `EvaluationReason` — knows nothing about HTTP, Redis,
JPA, or Spring. `FeatureFlagEvaluator.evaluate(...)` is a pure static function: given an
immutable snapshot of a flag and an immutable evaluation context, it returns an immutable result,
with no side effects and no framework dependency to mock in a test. This is the single
highest-value piece of code in the project — every feature check any consuming application makes
ultimately depends on it — and being framework-free is precisely what makes it exhaustively unit
testable (18 tests, including golden-vector cross-checks against an independent Python
implementation of the same hashing algorithm, to catch a subtle bit-shift or encoding bug that a
same-language test suite alone might share the same blind spot on).

`FeatureFlag` (the JPA entity, in `featureflag.domain`) is a deliberately distinct type from
`FeatureFlagSnapshot`: it knows about optimistic locking, its owning `Environment`/`User`
associations, and JPA lifecycle concerns; the snapshot knows about none of that.
`FeatureFlag.toSnapshot()` is the one-way bridge between them, called once per evaluation
(on a cache miss) or read directly from the cache (on a hit).

## 4.4 Request trace: evaluating a flag

```
Browser
  → POST /api/v1/flags/{id}/evaluate
  → JwtAuthenticationFilter          (validates the bearer token)
  → FeatureFlagController
  → EvaluationService
  → RedisFeatureFlagCache.get(id)     — hit: skip straight to the evaluator
                                       — miss: FeatureFlagRepository.findById (Postgres),
                                         populate the cache for next time
  → FeatureFlagEvaluator.evaluate     — pure function: disabled check → targeting rules →
                                         deterministic bucket
  → EvaluationResultDto               — back to the browser, with a Micrometer counter/timer
                                         recorded on the way out
```

## 4.5 Frontend structure

`frontend/src/features/<domain>/` colocates each domain's API functions, TanStack Query hooks,
and Zod schemas — `flags/`, `environments/`, `audit/`, `evaluation/`, `ai/`. `components/ui/` is
shadcn/ui primitives only (never domain logic); domain-specific components live in
`components/<domain>/`. TanStack Query owns all server state end to end — there is no ad-hoc
`useEffect` data fetching anywhere in the application. Zod schemas mirror the backend's Bean
Validation rules for fast client-side feedback, but the backend is always the actual authority;
nothing in the frontend is trusted as a substitute for server-side validation.

## 4.6 Caching architecture

Cache-aside on Redis, keyed by the flag's UUID (`feature-flag:{flagId}`), with every mutation
writing the fresh snapshot through to the cache in the same request that commits the Postgres
change — so the next evaluation after any edit reads the new value from cache directly, not
through a stale-then-expire window. Every Redis operation is wrapped so a Redis failure degrades
evaluation to "runs at Postgres speed," never to a failed request — verified by hand, not merely
asserted (stopping Redis and re-timing an evaluation call). Full rationale, including why the
cache is keyed by ID rather than `{environment}:{key}` given this project's specific API shape,
is in [ADR-002](../.claude/decisions/ADR-002-caching-strategy.md).

## 4.7 Live updates and metrics architecture (stretch goals)

`FeatureFlagService.create/update/delete` each publish a `FlagChangeEvent` from inside their own
transactional method; `FlagChangeNotifier` listens with `@TransactionalEventListener(phase =
AFTER_COMMIT)` and broadcasts to every subscribed `SseEmitter` only once the transaction has
actually committed — a mutation that rolls back is never seen by a subscriber as a change, because
the event fires strictly after commit. Subscriber state is an in-memory list, correct and
sufficient for this project's single-instance deployment target; a multi-instance deployment
would need a shared broadcast mechanism (Redis Pub/Sub, already a dependency here) to fan the
same event out to every instance's subscribers — documented, not built, since building it for a
target that doesn't exist would itself be unnecessary complexity. `GET /api/v1/flags/{id}/metrics`
reads the same Micrometer counters `EvaluationService` already increments, scoped and grouped per
flag, rather than adding a second write path for the same signal. Full rationale for every
decision in this section: [ADR-005](../.claude/decisions/ADR-005-stretch-goals.md).

# 5. Technology Stack & Rationale

| Layer | Choice | Rationale |
| --- | --- | --- |
| Backend runtime | Java 21, Spring Boot 3.5.16 | Latest stable Boot 3.x — deliberately not the newest available major (4.1.1): Boot 4 defaults to Jackson 3, but springdoc-openapi and jjwt (both used here) hadn't caught up at the time of building. Discovered empirically (an initial attempt to build against 4.1.1 failed with a missing `ObjectMapper` bean), not assumed in advance — see the `fix(backend): migrate to Spring Boot 3.5.16` commit. |
| Database | PostgreSQL 17, Flyway | Relational integrity for a genuinely relational domain — flags belong to environments, audit rows reference entities by foreign key. `ddl-auto=validate`: Flyway, not Hibernate, is the only thing that changes the schema. |
| Cache | Redis 8 | Sub-millisecond evaluation reads with an explicit invalidation story (write-through on every mutation), not "hope the TTL is short enough." |
| AI | Docker Model Runner (local, free) + a deterministic mock | No API key required to run or demo the project; the mock is the default and is a genuine second implementation of the same interface, not a stub that returns one canned response. |
| Frontend | Next.js 16 (App Router), React 19, TypeScript | A client-rendered SPA-style application talking to a separate backend — no server-side data fetching to speak of, so the App Router's value here is routing/tooling, not React Server Component data loading. |
| Styling | Tailwind CSS v4, shadcn/ui (Radix primitives) | CSS-variable-based semantic theming (light/dark via `next-themes`) with a custom indigo/violet brand palette rather than shadcn's neutral defaults. |
| Server state | TanStack Query | Owns all server state — no ad-hoc `useEffect` fetching anywhere in the application. |
| Tables | TanStack Table (v8-compatible legacy hook) | v9 replaced `useReactTable` with a new atom-store `useTable` API; this project only needs core row rendering (pagination/filtering are server-side), so the documented, stable `useLegacyTable` compatibility layer was the lower-risk choice for the actual requirement. |
| Forms | TanStack Form + Zod | Client-side validation mirroring the backend's Bean Validation, for fast feedback — the backend remains the sole authority. |
| Testing | JUnit 5, Mockito, Testcontainers, Vitest, React Testing Library | See §9 for the full breakdown. |
| Containerization | Docker, Docker Compose | Multi-stage builds for both services, non-root runtime users, healthchecks, named volumes for data persistence. |

# 6. Core Domain Concepts

| Concept | Description |
| --- | --- |
| **Environment** | A DEV/STAGING/PROD-style grouping that scopes flags. Create + list/get only — deliberately no update/delete, since environments are a low-churn concept in this domain and unused endpoints add complexity without a corresponding need. |
| **Feature Flag** | Belongs to exactly one environment; has a unique `key` within that environment, a `type` (`BOOLEAN` or `PERCENTAGE_ROLLOUT`), an `enabled` state, an optional `rolloutPercentage`, and a list of `TargetingRule`s. Carries a `@Version` column for optimistic concurrency. |
| **Targeting Rule** | An `attribute`/`operator`/`value` triple (e.g. `location NOT_EQUALS internal`) evaluated against the caller-supplied attributes at evaluation time — a targeting rule that doesn't match short-circuits the flag to `false` before rollout percentage is even considered. |
| **Evaluation Context** | The caller-supplied `stableIdentifier` (a durable per-user identifier — not a session ID) plus an arbitrary attribute map, passed to `POST /api/v1/flags/{id}/evaluate`. |
| **Evaluation Result** | `value` (true/false), `reason` (one of `FLAG_DISABLED`, `TARGETING_RULE_NOT_MATCHED`, `BOOLEAN_MATCH`, `ROLLOUT_INCLUDED`, `ROLLOUT_EXCLUDED`), the computed `bucket` (0-99) for a percentage rollout, and — for a targeting-rule failure — which rule didn't match. |
| **Audit Log** | One immutable row per CREATE/UPDATE/DELETE on a feature flag: actor, action, entity, environment, before/after JSON snapshots, the resulting version, and the correlation ID of the request that made the change. |

# 7. Key Architectural Decisions

Full rationale for each decision lives in `.claude/decisions/`; this section summarizes what
each one decided and why it mattered enough to write down.

## ADR-001 — Deterministic evaluation algorithm

**Decision:** percentage rollout bucket = `SHA-256("{flagKey}:{environment}:{stableIdentifier}")
mod 100`, never `Math.random()` or any other non-deterministic source.

**Why it matters:** a percentage rollout that used randomness per request would mean the same
user could see a feature on one request and off the next — an incoherent, untestable experience,
and the assessment explicitly calls out "no random-per-request percentage assignment" as a
requirement, not a suggestion. SHA-256 over a simpler hash (e.g. a 32-bit checksum) was chosen
for its well-understood, uniform bit distribution, so the 0-99 bucket a `mod 100` produces is
genuinely close to uniform across a large user population rather than clustering.

## ADR-002 — Caching strategy

**Decision:** cache-aside on Redis, keyed by flag UUID, write-through on mutation, every Redis
operation independently fault-tolerant (see §4.6 above for the mechanics).

**Why it matters:** this is the one endpoint in the platform designed to be called at real
application-traffic volume, and the assessment specifically asks for an evaluation endpoint
"designed for low latency" with an explanation of how it stays performant at scale. The
consistency-model section of the ADR is explicit about what this design does *not* guarantee — a
narrow, millisecond-scale window where a concurrent evaluation could read a value from just
before a mutation's cache write completes — rather than overstating the guarantee.

## ADR-003 — AI provider abstraction, model choice, validation pipeline

**Decision:** a two-layer abstraction, `AiRuleController → AiRuleAssistantService → AiProvider`,
where `AiProvider` is the narrowest possible seam (`String complete(systemPrompt, userPrompt)`)
with two implementations — `MockAiProvider` (default, a deterministic keyword parser, not a
stub) and `DockerModelRunnerAiProvider` (a real local LLM call). Every response from either
implementation passes through the identical pipeline: extract JSON → deserialize into
`RuleProposalDto` → Bean Validation (the exact same `TargetingRuleDto` type a human-submitted
rule is validated as) → domain-invariant validation → only then returned to the client, and never
persisted automatically.

**Why it matters:** the assessment is explicit that trusting AI output without validation is an
automatic deduction. Reusing the human-submission validation types for AI output, rather than
writing a parallel set of "AI-specific" validation rules, is what makes "AI output is validated
exactly like human input" a structural fact about the code rather than a policy someone has to
remember to enforce on the next change.

## ADR-004 — Single-column role instead of a join-table RBAC model

**Decision:** `User.role` is a single enum column (`ADMIN` / `VIEWER`), not a
`users`/`roles`/`user_roles` many-to-many join model.

**Why it matters:** the assessment asks for authorization protecting at least one operation by
role — a genuine RBAC join model (multiple roles per user, roles as first-class configurable
entities) would be the right design if the domain had more than two fixed, mutually exclusive
roles that needed to change without a code deploy. It doesn't, at this scope, and building the
join-table machinery for a two-value enum would be complexity with no corresponding requirement
behind it.

## ADR-005 — Stretch goals: live updates, evaluation metrics, sample SDK client

**Decision:** covered in detail in §4.7 above and in the ADR itself — SSE over WebSocket for live
updates (the traffic is genuinely one-way), reading Micrometer counters back rather than adding a
second write path for metrics, and a deliberately minimal, dependency-free sample SDK client
rather than a production-grade published package.

# 8. API Surface

Full request/response shapes, every status code each endpoint can return, and interactive
"Authorize and try it" access via Swagger UI live at `http://localhost:8080/swagger-ui.html`
when the backend is running. Summary of the surface:

| Area | Endpoints |
| --- | --- |
| Auth | `POST /api/v1/auth/login`, `GET /api/v1/auth/me` |
| Environments | `GET /api/v1/environments`, `GET /api/v1/environments/{id}`, `POST /api/v1/environments` (ADMIN) |
| Feature Flags | `GET /api/v1/flags`, `GET /api/v1/flags/{id}`, `POST /api/v1/flags` (ADMIN), `PUT /api/v1/flags/{id}` (ADMIN, optimistic concurrency), `DELETE /api/v1/flags/{id}` (ADMIN) |
| Evaluation | `POST /api/v1/flags/{id}/evaluate` |
| Evaluation metrics *(stretch goal)* | `GET /api/v1/flags/{id}/metrics` |
| Live updates *(stretch goal)* | `GET /api/v1/flags/stream` (Server-Sent Events) |
| Audit | `GET /api/v1/flags/{id}/audit`, `GET /api/v1/audit-logs` |
| AI rule assistant | `POST /api/v1/ai/rule-proposals` (never persists) |
| Operational | `GET /actuator/health`, `GET /actuator/metrics`, `GET /actuator/prometheus` |

Every 4xx/5xx response across every endpoint uses the same RFC 7807 `ProblemDetail` shape,
including a correlation ID that also appears in the corresponding server-side log line — there is
exactly one error shape in this API, not a different one per endpoint or per exception type.

# 9. Security

Summarized here; the full walkthrough against the assessment's own checklist (authN, authZ,
IDOR, injection, XSS, CSRF, SSRF, prompt injection, secrets, logging, mass assignment,
deserialization, dependency supply chain, CORS, headers, rate limiting) is
`docs/security.md`, including two findings from an automated security review during development
and exactly how each was fixed.

**Authentication:** stateless JWT (HS256), bcrypt-hashed passwords, an identical generic error on
a failed login regardless of whether the email or the password was wrong. `JWT_SECRET` and
`POSTGRES_PASSWORD` have no fallback default — the application refuses to start rather than boot
successfully with a known, guessable secret; this was itself a fix made in response to an
automated review finding on committed code.

**Authorization:** role-based, enforced server-side via `@PreAuthorize` on every mutating
endpoint, verified with a real HTTP request in `FeatureFlagApiTest` asserting a genuine `403` for
a VIEWER hitting an ADMIN-only endpoint — not merely asserted as a design intent.

**Prompt injection:** the AI system prompt explicitly instructs the model to treat the user's
natural-language input as data to extract targeting criteria from, never as instructions to
itself, and to ignore any attempt within it to override these instructions or claim to be a
system message. This is defense-in-depth alongside the output-side validation pipeline (§7,
ADR-003) — even a successful prompt-injection attempt against the model still has to produce
output that passes the identical Bean Validation and domain-invariant checks a human submission
does.

**Two bugs found and fixed via testing, not just reasoned about in advance:** (1) a request with
no token at all returned a bare `403` with no body instead of a proper `401` with the standard
error shape, because Spring Security's filter-level rejection happens before the application's
normal exception handler ever runs — fixed with dedicated entry-point/access-denied handlers
producing the same RFC 7807 shape as every other error. (2) The correlation ID header was
completely absent on that same rejected request, for the same underlying reason (a `@Component`-
registered filter runs *after* Spring Security's own chain by default) — fixed by wiring the
correlation ID filter directly into Security's own filter chain as the first thing it runs.

**Rate limiting is not implemented** and is documented as a real, named gap — not a silent
omission — in both `docs/security.md` and §11.2 below.

# 10. Testing Strategy & Documentation

## 10.1 Philosophy

Automated tests verify correctness of individual units and API contracts; they do not, on their
own, prove the application actually works end to end. Several of the most significant bugs found
during this project (§13.2) were only caught by actually running the full stack — native backend,
native frontend, real Postgres, real Redis — and exercising it with a real headless browser or
real `curl` calls, *after* the automated suites were already green. This is documented explicitly
because it's a genuine, load-bearing practice this project followed, not a retrospective
rationalization: `mvn test` passing and `tsc --noEmit` passing are necessary, not sufficient.

## 10.2 Backend test suite — 58 tests

| Test class | Count | What it covers |
| --- | :---: | --- |
| `FeatureFlagEvaluatorTest` | 18 | The framework-free evaluation core: disabled flags, boolean matches, targeting-rule matching/non-matching, deterministic percentage-rollout bucketing, and golden-vector cross-checks against an independent Python implementation of the same SHA-256 hashing algorithm. |
| `AiRuleAssistantServiceTest` | 11 | Every stage of the AI validation pipeline against a mocked `AiProvider`: successful extraction, JSON wrapped in prose/markdown fences, malformed JSON, failed Bean Validation, failed domain-invariant validation, and all five `AiFailureReason` values collapsing to the correct `503`. |
| `FeatureFlagApiTest` | 10 | Full-stack HTTP tests via MockMvc against a real Spring context (Testcontainers Postgres/Redis): auth requirement, role-based authorization (`403` for VIEWER on ADMIN-only routes), field-level validation errors, `404` shape, pagination, correlation ID propagation (both generated and client-supplied), the new metrics endpoint, and the new SSE stream endpoint's auth requirement and immediate `connected` event on the wire. |
| `MockAiProviderTest` | 4 | The deterministic keyword-parsing mock provider: the assessment's own canonical example, percentage extraction, exclusion-clause handling, and the plain-BOOLEAN fallback when a percentage can't be confidently extracted. |
| `CorrelationIdFilterTest` | 4 | Header generation when absent, echoing a valid client-supplied ID, and rejecting an invalid one (including a literal CRLF/response-splitting injection payload) in favor of a freshly generated UUID. |
| `FeatureFlagServiceIntegrationTest` | 4 | Against real Postgres via Testcontainers, not a mocked repository — the exact class of bug (`saveAndFlush` vs `save` version-staleness) a mock would have hidden entirely. Also proves, with a real Spring event listener (not a mock), that create/update/delete each publish exactly one `FlagChangeEvent`, and only after their transaction commits. |
| `FlagChangeNotifierTest` | 4 | SSE broadcast/cleanup logic in isolation: a completed emitter genuinely rejects further sends (the assumption the notifier's cleanup logic relies on), broadcasting with zero/multiple/a mix of live-and-disconnected subscribers never throws. |
| `AuthServiceTest` | 2 | Successful login issuing a valid token; a wrong password producing the correct generic authentication failure. |
| `BackendApplicationTests` | 1 | The Spring context loads cleanly with all beans wired — the cheapest possible regression test against a broken bean graph. |

## 10.3 Frontend test suite — 19 tests

| Test file | Count | What it covers |
| --- | :---: | --- |
| `data-table.test.tsx` | 4 | Loading state, empty state, rendered rows, and pagination controls of the shared `DataTable` component. |
| `evaluation-playground.test.tsx` | 3 | Evaluation result rendering for each reason, including the targeting-rule-mismatch detail view. |
| `ai-rule-assistant-dialog.test.tsx` | 3 | The AI proposal review flow, including the AI-unavailable (`503`) path rendering the correct, non-duplicated error copy. |
| `flag-form-dialog.test.tsx` | 3 | Required-field validation blocking submission, and the `409` stale-version-conflict path surfacing explicitly rather than silently retrying or overwriting. |
| `sse-client.test.ts` | 6 | The hand-rolled SSE wire-format parser: named events, the `message` default when no `event:` line is present, multi-line `data:` joining per the SSE specification, and comment/heartbeat lines correctly producing no event. |

## 10.4 What automated tests deliberately do not cover, and why

- **A full `docker compose up --build` run.** Not empirically completed in this development
  environment due to sustained local bandwidth constraints (see §11 and the README's Known
  Limitations) — mitigated by validating `docker compose config` (catches env-var interpolation
  and wiring errors without a network pull), a line-by-line Dockerfile review, and running the
  exact same build commands each Dockerfile runs (`./mvnw clean package`, `pnpm build`) natively.
- **Live inference quality from the Docker Model Runner path across arbitrary inputs.** The
  validation pipeline is exhaustively tested against a mocked provider (11 tests covering every
  failure mode); a live call against a real pulled model was additionally made by hand for the
  assessment's own canonical example (see §13.2) to confirm the real integration works, but
  fuzzing arbitrary natural-language input against a live LLM is outside this project's test
  suite by design — the validation pipeline, not the model's raw output, is what's contractually
  guaranteed.

# 11. Known Limitations

Consolidated from the README's Known Limitations section — reproduced here so this specification
is self-contained, not because the list has changed:

- No dashboard aggregation endpoint (computed client-side from a generously-paginated flags
  query; fine at this project's demo data volume).
- No server-side flag text/key search (environment filtering is server-side; free-text search
  would need a new endpoint, not built given the scope decision).
- Environments have create + list/get only, no update/delete (a low-churn concept in this
  domain).
- JWT stored in `localStorage`, not an httpOnly cookie (no Next.js backend-for-frontend to set
  one on the same origin) — mitigated by a short, 1-hour default token lifetime.
- No refresh tokens or server-side token revocation — stateless access tokens only.
- Redis integration tests use a generic Testcontainers container rather than a dedicated
  Redis-specific test module.
- `docker compose up --build` was not empirically completed end to end in this development
  environment (severe local bandwidth constraints) — see §10.4 above for what was verified
  instead.
- No rate limiting anywhere in the API — a real, named gap, most relevant on
  `/api/v1/auth/login` (brute-force resistance).

# 12. Trade-offs Made

Distinct from limitations (gaps not built) — these are choices made deliberately, with a real
alternative considered and rejected for a stated reason:

| Trade-off | Chosen | Alternative considered | Why |
| --- | --- | --- | --- |
| Token storage | `localStorage` | httpOnly cookie | No same-origin backend-for-frontend exists to set one; mitigated with a short token lifetime. |
| Audit immutability enforcement | Application-layer (no `delete` method exists in the repository type hierarchy) | Database-layer triggers | Closes the gap application code can reach; a DB-level trigger (documented in `docs/production-readiness.md`, not built) is the correct next layer for a real deployment, but more machinery than a take-home needs to make the point. |
| Live-update transport | Server-Sent Events | WebSocket | The traffic is genuinely one-way; a bidirectional channel would be unused capability. |
| Metrics storage | Read Micrometer's existing counters back | A dedicated evaluation-events table | The stretch goal asks for basic counts, which the platform already collects; a new write path would duplicate an existing signal for a bigger feature (durable, queryable history) than was actually requested. |
| Roles | Single enum column | Join-table RBAC | Two fixed, mutually exclusive roles don't need configurable-without-a-deploy machinery. |
| Rollout hash | SHA-256 | A faster, non-cryptographic hash (e.g. MurmurHash) | Uniform bit distribution matters more here than raw hashing speed — this runs once per evaluation, not in a hot inner loop. |

# 13. Improvement Roadmap

## 13.1 Scale

The backend and frontend are both already stateless — horizontal scaling is "run more instances
behind a load balancer," not a redesign. In rough order of what would need attention first under
real production load: the Postgres connection pool (currently unbounded default HikariCP
sizing) would need explicit tuning once there's more than a handful of backend instances; a
single Redis instance has no failover (a restart today is a brief latency blip via the Postgres
fallback, not an outage, but sustained loss means every evaluation pays that cost until it's
back); a single Postgres instance has no read replicas. See `docs/production-readiness.md` for
the fuller treatment, including the specific Postgres trigger SQL that would close the
audit-immutability gap at the database layer.

## 13.2 Security

Highest-priority additions beyond what's already in place (§9): rate limiting on
`/api/v1/auth/login` at minimum; a real secrets manager (cloud KMS-backed) instead of environment
variables on disk, with `JWT_SECRET` rotation support the current design doesn't have (rotating
it today invalidates every outstanding token immediately, with no grace-period dual-key
verification); a Postgres-level trigger rejecting `UPDATE`/`DELETE` on `audit_logs` outright, to
close the last layer the application-level `AppendOnlyRepository` guarantee doesn't reach (a raw
SQL statement with direct database access).

## 13.3 Observability

Structured JSON logs, correlation IDs, and Micrometer counters/timers are already in place and
already shaped for the natural next step: an OpenTelemetry Collector shipping them to a durable
backend (self-hosted or vendor), rather than the current state where they only live in
`docker compose logs` until a container recycles.

## 13.4 Multi-instance live updates and metrics

As detailed in ADR-005: a multi-instance deployment would need the SSE broadcast mechanism to
fan out across instances via a shared broker (Redis Pub/Sub, already a dependency here) rather
than each instance's local in-memory subscriber list, and the per-flag metrics endpoint would
need to aggregate across instances rather than reading one process's local Micrometer registry
(the raw Prometheus-format aggregate endpoint already handles this correctly today, via
Prometheus's own scrape-and-aggregate model).

## 13.5 Developer experience

A CI pipeline running both test suites as a merge gate is the natural next addition — the test
suites themselves are already fast enough (a few seconds each) to make this cheap. A rolling or
blue-green deploy strategy would prevent a bad deploy from taking evaluation traffic down.

## 13.6 Feature surface

Beyond the stretch goals already implemented: a backend dashboard-aggregation endpoint (once
flag counts grow past what a single generously-paginated query comfortably handles), server-side
flag text search, and environment update/delete if the DEV/STAGING/PROD set ever needs to change
without a migration.

# 14. Suggested Demo Flow

A condensed version; the full, narrated walkthrough is `docs/Demo_Video_Script.md` (and its
accompanying `.docx`):

1. Log in as `admin@example.com`; show the dashboard.
2. Create a `PERCENTAGE_ROLLOUT` flag with a targeting rule.
3. Run the evaluation playground for two different `stableIdentifier` values; show the bucket
   stays consistent on repeat calls.
4. Edit the flag; show the version bump and the audit diff.
5. Demonstrate the `409` stale-version conflict from a concurrent edit.
6. Use the AI Rule Assistant on the assessment's own example sentence; show the labeled
   proposal, apply it, and show it pre-fills the create form untouched until explicitly
   submitted.
7. **Failure path**: stop Redis, evaluate a flag again, show it still resolves correctly (just
   slower) via the Postgres fallback.
8. Run `./mvnw test` and `pnpm test`; show both suites passing.
9. **Stretch goals**: the "Live" indicator in the topbar; edit a flag from a second tab and watch
   the first tab's flags list update with no refresh; the per-flag metrics card; run
   `node examples/sdk-client/example.mjs` in a terminal.

# 15. AI-Assisted Development Disclosure

This project was built with substantial use of Claude Code (Anthropic), operating largely
autonomously across most of the implementation: project scaffolding, backend domain modeling and
the evaluation engine, the caching/audit/AI-provider abstractions, the full frontend, both test
suites, Docker configuration, and this documentation.

Concretely: Claude Code read the assessment PDF directly and treated it as the authoritative
requirements source over the candidate's own earlier, more expansive draft prompt; made and
documented the scope-calibration call to trim that draft to a lean, rubric-optimized feature set;
wrote essentially all source code and tests; ran the actual application (backend, frontend,
Postgres, Redis, and Docker Model Runner) locally throughout development rather than relying only
on type-checking; and used a real headless browser to click through every major flow by hand,
which is how several real bugs were caught — detailed with full specificity in §9 and §10.4
above, and in the README's own AI-assisted-development-disclosure section.

The candidate directed scope, reviewed the resulting architecture and trade-offs throughout, and
takes ownership of and can explain every part of the submitted implementation.

# Appendix A: Environment Variables

Full documentation with safe placeholder values lives in `.env.example`. Summary:

| Variable | Purpose |
| --- | --- |
| `POSTGRES_DB` / `_USER` / `_PASSWORD` / `_HOST` / `_PORT` | Database connection |
| `REDIS_HOST` / `REDIS_PORT` | Cache connection |
| `JWT_SECRET` | HMAC signing key — required, no default |
| `JWT_EXPIRATION_MINUTES` | Access token lifetime (default 60) |
| `CACHE_FLAG_TTL_SECONDS` | Redis TTL safety net (default 300) |
| `CORS_ALLOWED_ORIGINS` | Origins the backend accepts browser requests from |
| `AI_PROVIDER` | `mock` (default) or `docker-model-runner` |
| `AI_BASE_URL` / `AI_MODEL` / `AI_TIMEOUT_MS` | Only used when `AI_PROVIDER=docker-model-runner` — see the README's "Switching the AI model" section |
| `NEXT_PUBLIC_API_URL` | Backend URL the browser calls, baked into the frontend build |

# Appendix B: Demo Accounts

| Email | Password | Role |
| --- | --- | --- |
| `admin@example.com` | `Password123!` | `ADMIN` — full access |
| `viewer@example.com` | `Password123!` | `VIEWER` — read + evaluation playground only |

# Appendix C: Quick Start

```bash
git clone <this-repo-url>
cd "Feature Flag & Configuration Platform"
cp .env.example .env
# Edit .env: set a real JWT_SECRET (openssl rand -base64 48)

docker compose up --build
```

Frontend: `http://localhost:3000`. Backend / Swagger UI: `http://localhost:8080/swagger-ui.html`.
Full instructions, including native (non-Docker) development, are in the project README.
