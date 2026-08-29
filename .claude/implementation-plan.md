# Implementation Plan

Phased plan, lean/rubric-optimized scope. Each phase ends with a working build and a commit.
Update `current-state.md` as phases complete — this file is the map, not the log.

1. **Scaffolding** — `.claude/` docs (this), `.gitignore`, root `docker-compose.yml` skeleton,
   backend Maven project (Spring Initializr-equivalent, package-by-feature skeleton), frontend
   Next.js project (App Router, Tailwind, shadcn init).
2. **Database & migrations** — Flyway `V1..Vn`: users, environments, feature_flags, audit_logs.
   Constraints: unique flag key per environment, unique environment name, valid rollout range,
   non-null actor on audit rows.
3. **Domain model & evaluation engine** — framework-free `EvaluationContext`,
   `FeatureFlagSnapshot`, `TargetingRule`, `RolloutStrategy`, `FeatureFlagEvaluator`. Unit tests
   first-class here (deterministic hashing, boundary buckets, targeting rule matching).
4. **Auth** — Spring Security + JWT, `ADMIN`/`VIEWER` roles, login endpoint, method-level
   `@PreAuthorize` on mutating endpoints.
5. **Environments & Feature Flags REST API** — CRUD, Bean Validation, optimistic locking
   (`@Version` → 409 Problem Details), pagination/filtering on list endpoints.
6. **Redis caching** — `FeatureFlagCache` interface + Redis impl, cache-aside on evaluation,
   explicit invalidation on every mutation, graceful fallback to Postgres if Redis is down.
7. **Audit system** — immutable `audit_logs` rows (JSONB previous/new value) written in the
   same transaction as every mutation; audit list/detail endpoints.
8. **AI rule assistant** — `AiRuleAssistant`/`AiProvider` abstraction, `DockerModelRunnerAiProvider`
   + `MockAiProvider`, strict schema + whitelist validation, `/api/v1/ai/rule-proposals` endpoint
   that only ever returns a proposal (never persists).
9. **Observability** — correlation ID filter, structured JSON logs, Actuator health/info,
   Micrometer counters (evaluations, cache hit/miss, AI requests/failures).
10. **Backend tests** — fill gaps: MockMvc API tests (auth/authz/validation/pagination/errors),
    Testcontainers integration tests (Postgres + Redis), AI provider mock tests (5 failure modes).
11. **Frontend shell** — app shell, theming (next-themes), auth (login, protected routes,
    token handling), TanStack Query provider setup.
12. **Flags dashboard & CRUD** — flag list (TanStack Table, server pagination), create/edit
    forms (TanStack Form + Zod), flag detail view, stale-version conflict UX.
13. **Evaluation playground** — user-context form → `/evaluate` call → resolved result, reason,
    bucket, matched rule.
14. **AI Rule Assistant UI** — prompt input → proposal card (editable) → explicit apply → toast
    + audit entry appears.
15. **Audit log UI** — table with filters, pagination, diff view of previous/new value.
16. **Frontend tests** — Vitest + RTL on the components that carry real logic/state (playground,
    AI proposal review, form validation, stale-conflict banner).
17. **Dockerization** — multi-stage Dockerfiles (backend, frontend), non-root users, healthchecks,
    named volumes for Postgres/Redis persistence, full `docker-compose.yml`.
18. **Documentation & final pass** — README (all required sections), `docs/` (architecture,
    security, ai, production-readiness), `.env.example`, compliance checklist, run the full
    test suite + docker compose up from clean, fix anything broken.
