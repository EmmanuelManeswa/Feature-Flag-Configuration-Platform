# Current State

Last updated: 2026-08-30 (session 1, continued after a mid-session resumption)

## Completed
- Read the authoritative assessment PDF; scope calibrated with candidate as
  **lean & rubric-optimized**, **autonomous phase-by-phase commits** (see
  `CLAUDE.md`).
- Repo scaffolded: backend (Spring Boot **3.5.16** — downgraded from an
  initial 4.1.1 after hitting a real Jackson 2/3 ecosystem incompatibility,
  see the `fix(backend): migrate to Spring Boot 3.5.16` commit and
  `git log` for the full story), frontend (Next.js 16 / React 19 / Tailwind 4,
  scaffolded, not yet built out beyond `create-next-app` defaults —
  **read `frontend/node_modules/next/dist/docs/` before writing App Router
  code**, this Next.js version has real breaking changes from training data
  per its own `AGENTS.md`).
- `.claude/` docs: CLAUDE.md, implementation-plan.md, this file,
  `decisions/` (ADR-001 evaluation algorithm, ADR-002 caching strategy,
  ADR-004 simple roles; ADR-003 AI provider abstraction not yet written —
  do that alongside the AI phase).
- Postgres + Redis running via `docker-compose.yml` (host ports 5433/6380 —
  deliberately non-standard, this machine runs native Postgres/Redis on the
  default ports too). Named volumes, data persists across restarts.
- Flyway schema V1-V6 (users, environments, feature_flags, audit_logs,
  indexes, seed data) applied and verified against real Postgres.
- **Evaluation engine** (`evaluation.domain`, framework-free): deterministic
  SHA-256 bucketing, targeting rules, 18 unit tests including golden vectors
  cross-checked against an independent Python implementation.
- **Auth**: JWT (jjwt), Spring Security, ADMIN/VIEWER roles,
  `@PreAuthorize` on mutating endpoints, `/api/v1/auth/login` + `/me`.
- **Environments API**: list/get/create.
- **Feature Flags API**: full CRUD, optimistic concurrency (409 with
  before/after version), `/evaluate` (cache-aside Redis, Postgres fallback,
  ~10x faster on cache hit, verified Redis-down resilience by hand:
  36ms fallback after tightening the Lettuce timeout from 2s to 300ms —
  see the caching commit for why 2s was actually a ~4s-per-request bug),
  `/audit`.
- **Audit trail**: immutable (`AppendOnlyRepository` — no delete method
  anywhere in the type hierarchy, not just "nothing calls it"), before/after
  JSONB, correlation ID, written in the same transaction as every mutation.
- **Observability**: correlation ID filter (validated against an allowlist
  — an automated review caught a response-splitting/log-injection gap in
  the first version), RFC 7807 `ProblemDetail` everywhere via
  `GlobalExceptionHandler`, structured JSON logs in docker/prod profile,
  Micrometer counters (cache hit/miss, evaluations per flag/result) +
  latency timer, Actuator health/prometheus.
- Swagger/OpenAPI: full `@Operation`/`@ApiResponses` per endpoint per status
  code, bearer-auth security scheme wired so Swagger UI's Authorize button
  drives every protected endpoint directly (user explicitly asked for this
  to be "super detailed" — keep that bar for AI endpoints too).
- 28 backend tests passing (unit + Testcontainers integration), all real,
  no mocked-away correctness gaps discovered so far without also being fixed.
- End-to-end manually smoke-tested against the real running app + real
  Postgres/Redis (not just `mvn test`): login, CRUD, evaluate (cache hit/
  miss/targeting), stale-version 409, RBAC 403, validation 400s, duplicate-
  key rejection, delete + 404, global and per-flag audit lists, Redis-down
  resilience. This is how the EntityGraph bug, the saveAndFlush bug, and the
  Redis timeout problem were actually found — none of them showed up in
  `mvn test` with the app never actually run end-to-end.

## Current work
- About to start the AI rule assistant (`ai/` package): provider
  abstraction (`AiRuleAssistant`/`AiProvider`), `MockAiProvider` (default,
  always available) + `DockerModelRunnerAiProvider` (confirmed running
  locally on this machine, no model pulled yet — pick one and document why
  in ADR-003 before wiring it up).

## Remaining
- AI rule assistant (backend) + ADR-003.
- Observability polish pass (confirm `/actuator/prometheus` output looks
  sane once there's real traffic to show).
- Frontend: everything beyond the raw scaffold — shell/auth, flags
  dashboard + CRUD forms (TanStack Query/Table/Form + Zod), evaluation
  playground, AI rule assistant UI, audit log UI, Vitest+RTL tests.
- Full docker-compose (backend + frontend services, multi-stage Dockerfiles,
  non-root users, healthchecks).
- README + docs/ (architecture, security, ai, production-readiness) +
  compliance checklist.
- Backend still needs: MockMvc-level API tests for auth/authz/pagination/
  error-shape (currently covered indirectly by manual smoke-testing and the
  Testcontainers integration test, but not by an automated API-layer test
  suite), AI provider mock tests (5 failure modes per assessment: malformed
  JSON, invalid schema, provider unavailable, timeout, empty response).

## Known bugs
- None currently open. Three were found and fixed this session (see
  `feat(backend): feature flags REST API...` commit for two of them, and
  the Redis timeout note in the caching commit for the third) — all via
  manual end-to-end testing, not automated tests, which is itself the
  lesson: keep running the real app against real Postgres/Redis
  periodically, don't rely on `mvn test` alone to catch integration-shaped
  bugs.

## Architectural decisions made
- Spring Boot 3.5.16, not 4.1.1 — ecosystem compatibility over bleeding
  edge (see ADR list; consider writing a short ADR-006 for this if the
  reasoning needs to be referenced again, currently it's only in the git
  commit message).
- Cache keyed by flag UUID, not `{environment}:{key}` — ADR-002.
- Deterministic SHA-256 bucketing — ADR-001.
- Single-column role, no join table — ADR-004.

## Next recommended task
- `ai/` package: `AiProvider`/`AiRuleAssistant` interfaces, `MockAiProvider`,
  strict schema + whitelist validation of whatever comes back, then
  `POST /api/v1/ai/rule-proposals` (proposal only — never persists).
  `DockerModelRunnerAiProvider` after the mock path works and is tested.
