# Current State

Last updated: 2026-08-30 (session 1, continued across a mid-session resumption and a
usage-limit resumption)

## Completed — this is essentially the full lean-scope implementation

**Backend** (Spring Boot 3.5.16, 50 tests passing):
- Evaluation engine (framework-free), deterministic SHA-256 bucketing — ADR-001
- Auth: JWT, Spring Security, ADMIN/VIEWER roles
- Environments API (create/list/get)
- Feature Flags API: full CRUD, optimistic concurrency (409), evaluate, per-flag audit
- Redis caching (cache-aside, keyed by flag ID) — ADR-002, verified resilient to Redis being
  down by hand (36ms fallback after tuning the Lettuce timeout down from 2s)
- Immutable audit trail (`AppendOnlyRepository` — no delete method exists in its hierarchy)
- AI rule assistant: `AiProvider`/`AiRuleAssistantService` abstraction, `MockAiProvider`
  (default) + `DockerModelRunnerAiProvider` — ADR-003
- Observability: correlation IDs (now correctly wired ahead of Spring Security's own chain),
  RFC 7807 errors everywhere including filter-level 401/403, structured JSON logs (docker/prod
  profile), Micrometer counters/timer, Actuator
- Full OpenAPI/Swagger docs per endpoint per status code, bearer-auth wired into Swagger UI

**Frontend** (Next.js 16 / React 19, 13 tests passing):
- shadcn/ui (Radix base) + custom indigo/violet brand theme (light/dark via next-themes)
- Auth (JWT in localStorage, documented trade-off), protected app shell, sidebar/topbar
- Dashboard, Feature Flags list+create+edit (TanStack Table/Form/Query), flag detail page
- Evaluation playground, AI Rule Assistant dialog (apply → pre-fills create form, never
  auto-saves), Audit Log (global + per-flag, with a curated diff view), Environments
- Vitest + RTL tests for the states that actually matter: evaluation result rendering, AI
  proposal review (including the AI-unavailable path), 409 stale-version conflict, DataTable
  loading/empty states

**Docker**: multi-stage Dockerfiles for both services (non-root, healthchecks), full
`docker-compose.yml` (postgres/redis/backend/frontend), named volumes for persistence.

**Docs**: README (all required sections), `docs/security.md`, `docs/production-readiness.md`,
`docs/assessment-compliance.md`, ADR-001 through ADR-004.

## Real bugs found via actually running the app (not just type-checking / mvn test)

All fixed, all now have a regression test or were verified fixed by hand:

1. `FeatureFlagRepository.findById` needed `@EntityGraph(attributePaths = "environment")` —
   without it, a `LazyInitializationException` outside any transaction (exactly where
   `EvaluationService` deliberately operates, per ADR-002).
2. `FeatureFlagService.update` used `save()` instead of `saveAndFlush()` — the API response and
   audit record were reporting the pre-update version, which would have made the next
   legitimate edit look falsely stale.
3. Lettuce's default 2s Redis timeout made "Redis down" evaluations take ~4s (a failed GET, then
   a failed PUT) instead of the intended fast fallback — tuned to 300ms.
4. Filter-level (no-token) requests returned a bare 403 instead of 401 with no RFC 7807 body,
   and had no correlation ID — `CorrelationIdFilter` was registered *after* Spring Security's
   own chain by default. Fixed with dedicated entry-point/access-denied handlers and by wiring
   the filter directly into Security's chain.
5. Frontend: applying an AI proposal dumped its truncated explanation into the flag Name field;
   the AI-unavailable error message duplicated "configure manually" (both frontend and backend
   copy said it).

Two automated-review findings on committed code, also fixed: `JWT_SECRET`/`POSTGRES_PASSWORD`
had insecure fallback defaults (removed — now required, no default); the
`X-Correlation-ID` request header was reflected unvalidated (response-splitting/log-injection
risk) — now allowlist-validated.

## Environment notes for future sessions

- This machine had significant network/bandwidth constraints during this session (Docker Hub
  pulls, npm/pnpm installs, and a Docker Model Runner model pull all ran extremely slowly — the
  `ai/llama3.2` pull took multiple hours for ~2GB). If picking this up again and something
  seems to hang on a download, that's likely why — check `docker model df` / process activity
  before assuming something is broken.
- `bun` (needed for the `gstack` browse skill) is installed via Homebrew
  (`brew install oven-sh/bun/bun`) — the skill's own pinned-checksum curl installer failed a
  checksum match (bun.sh's install script had moved on from the pinned hash).
- Node/pnpm need `export PATH="$HOME/.nvm/versions/node/v23.11.0/bin:$PATH"` in some shell
  invocations this session — profile sourcing was inconsistent across Bash tool calls at times.

## Current work

Was running a full `docker compose up -d --build` (fresh build of both service images against
the real Dockerfiles, not just `docker compose up` against already-running dev servers) to
verify the reviewer's actual `git clone && docker compose up` path works end to end. Slow due to
the network conditions above — check `docker compose ps` and `docker compose logs` for where it
got to.

## Remaining

- Finish verifying the fresh `docker compose up --build` actually serves the app correctly on
  :3000/:8080 (was verified extensively via `mvnw spring-boot:run` + `pnpm dev` directly, and via
  real browser testing against that setup — Docker-specific verification (build args, healthchecks,
  inter-container networking) was still in progress when this was last updated).
- Live end-to-end verification of `DockerModelRunnerAiProvider` against a real pulled model —
  the mock provider (documented default) is fully verified; the Docker Model Runner path is
  verified against the API contract and covered by mocked-provider tests. The `ai/llama3.2`
  pull was retried across the session and ultimately **failed**, not just slow: it reached
  ~202MB of 2.02GB then hit a blob digest mismatch (`docker model pull ai/llama3.2`, checksum
  error), consistent with the same degraded-connection pattern seen with Docker Hub image
  pulls all session. `docker model list` confirms no model is present locally. Not retried
  further without the candidate's go-ahead, to avoid tying up the session on the same failure
  mode again. This is disclosed honestly in the README rather than claimed as verified.
- Optional stretch goals not attempted (SSE/WebSocket flag-change updates, an SDK-style sample
  client, none required for the lean scope decided with the candidate).

## Next recommended task

Confirm the Docker build finished cleanly (`docker compose ps`, then hit `localhost:3000` and
`localhost:8080/actuator/health`); if the AI model finished pulling, flip `.env`'s
`AI_PROVIDER` to `docker-model-runner` and do one live verification pass, then flip it back to
`mock` (the documented default) before finishing. Otherwise, this is essentially done —
mark the submission (git tag `submission` or note the final commit SHA) and do a final read
through the README as if seeing it for the first time.
