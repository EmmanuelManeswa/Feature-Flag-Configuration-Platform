# Current State

Last updated: 2026-08-31 (session 2 — stretch goals, live AI verification, documentation
deliverables, Swagger response-coverage audit, admin user management)

## Completed — full lean-scope implementation, all three stretch goals, plus user management

**Backend** (Spring Boot 3.5.16, 77 tests passing):
- Evaluation engine (framework-free), deterministic SHA-256 bucketing — ADR-001
- Auth: JWT, Spring Security, ADMIN/VIEWER roles, self-service password change
  (`PUT /api/v1/auth/me/password`)
- Environments API (create/list/get)
- Feature Flags API: full CRUD, optimistic concurrency (409), evaluate, per-flag audit
- Redis caching (cache-aside, keyed by flag ID) — ADR-002, verified resilient to Redis being
  down by hand (36ms fallback after tuning the Lettuce timeout down from 2s)
- Immutable audit trail (`AppendOnlyRepository` — no delete method exists in its hierarchy)
- AI rule assistant: `AiProvider`/`AiRuleAssistantService` abstraction, `MockAiProvider`
  (default) + `DockerModelRunnerAiProvider` — ADR-003, live-verified against a real pulled
  `ai/llama3.2` model
- Observability: correlation IDs (wired ahead of Spring Security's own chain), RFC 7807 errors
  everywhere including filter-level 401/403, structured JSON logs (docker/prod profile),
  Micrometer counters/timer, Actuator
- Full OpenAPI/Swagger docs per endpoint per status code, bearer-auth wired into Swagger UI —
  audited every controller against the live `/v3/api-docs` JSON this session and found/fixed a
  systematic gap (every unrestricted GET endpoint was missing its own genuinely-reachable `401`)
- **Stretch goal — live updates**: `GET /api/v1/flags/stream` (SSE), `FlagChangeNotifier`
  broadcasting only `AFTER_COMMIT` — ADR-005
- **Stretch goal — evaluation metrics**: `GET /api/v1/flags/{id}/metrics`, reads the existing
  Micrometer counters back scoped/grouped per flag — ADR-005
- **User management (beyond assessment scope)**: `POST /api/v1/users` (ADMIN, backend-generated
  password returned once), `GET /api/v1/users`, `POST /api/v1/users/{id}/disable`\|`/enable`
  (ADMIN, self-disable blocked with a 400) — ADR-006. `V7__add_user_enabled_flag.sql`.

**Frontend** (Next.js 16 / React 19, 23 tests passing):
- shadcn/ui (Radix base) + custom indigo/violet brand theme (light/dark via next-themes)
- Auth (JWT in localStorage, documented trade-off), protected app shell, sidebar/topbar
- Dashboard, Feature Flags list+create+edit (TanStack Table/Form/Query), flag detail page
- Evaluation playground, AI Rule Assistant dialog (apply → pre-fills create form, never
  auto-saves), Audit Log (global + per-flag, with a curated diff view), Environments
- Vitest + RTL tests for the states that actually matter: evaluation result rendering, AI
  proposal review (including the AI-unavailable path), 409 stale-version conflict, DataTable
  loading/empty states, hand-rolled SSE event parser, create-user flow
- **Stretch goal — live updates UI**: `lib/sse-client.ts` (fetch + ReadableStream, not
  `EventSource` — see ADR-005 for why), `LiveUpdatesIndicator` in the topbar, flags list/
  dashboard auto-refresh on any flag change from any source
- **Stretch goal — metrics UI**: `FlagMetricsCard` on the flag detail page
- **User management UI**: `/users` page (ADMIN-only nav link, and an explicit "Admins only"
  message rather than a silently-empty table if a VIEWER navigates there by URL), create-user
  dialog with a one-time generated-password reveal + copy button, disable/enable with a
  confirmation dialog, "Change password" in the user menu (any role)

**Stretch goal — sample SDK client**: `examples/sdk-client/` — plain Node.js, zero dependencies,
`client.mjs` + a runnable `example.mjs` demo. Live-verified twice against the real backend,
confirming identical deterministic-rollout results both runs.

**Docker**: multi-stage Dockerfiles for both services (non-root, healthchecks), full
`docker-compose.yml` (postgres/redis/backend/frontend), named volumes for persistence.

**Docs**: README (all required sections plus "Stretch goals", "Switching the AI model", "Data &
log persistence", "User management"), `docs/security.md`, `docs/production-readiness.md`,
`docs/assessment-compliance.md`, ADR-001 through ADR-006, `docs/Technical_Specification.md`/
`.docx` (kept in sync with every addition above).

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
6. `AI_MODEL`'s default had silently drifted to `ai/qwen2.5:latest` even though ADR-003
   documents `ai/llama3.2` as the actual chosen model — found while live-verifying the AI path
   for the first time this session, fixed in `application.yml`, `.env.example`, and
   `docker-compose.yml`.
7. `AI_TIMEOUT_MS=8000` was too aggressive for a real local model — a cold-loaded `ai/llama3.2`
   took ~14-18s before first token even though actual llama.cpp inference was under a second
   once warm, so a real, successful response was being clipped as a false `TIMEOUT`. Only
   reachable by calling a real pulled model, not the mocked-provider tests. Raised to 20000ms.
8. `JwtAuthenticationFilter`'s per-request authentication path builds its `Authentication`
   directly and never goes through `DaoAuthenticationProvider` — so it had no automatic
   `isEnabled()` check, meaning a token issued before an account was disabled would have kept
   working until natural expiry. Fixed by checking `isEnabled()` explicitly in the filter,
   against a fresh per-request database read. See ADR-006.
9. `CreateUserDialog`'s clipboard-copy button had no error handling — a denied/unavailable
   Clipboard API (verified live in the headless browser test environment, but the same failure
   mode applies to a non-secure context or a user declining the permission in any real browser)
   left the button silently doing nothing, with zero feedback. Fixed with a try/catch and a
   "copy it manually" toast; regression-tested.

Two automated-review findings on committed code, also fixed: `JWT_SECRET`/`POSTGRES_PASSWORD`
had insecure fallback defaults (removed — now required, no default); the
`X-Correlation-ID` request header was reflected unvalidated (response-splitting/log-injection
risk) — now allowlist-validated.

## Environment notes for future sessions

- This machine had significant network/bandwidth constraints throughout development (Docker Hub
  pulls, npm/pnpm installs, and the Docker Model Runner model pull all ran extremely slowly, and
  the `ai/llama3.2` pull failed outright more than once with a blob checksum mismatch before
  eventually succeeding). If picking this up again and something seems to hang on a download,
  check for this before assuming something is broken in the app itself.
- `bun` (needed for the `gstack` browse skill) is installed via Homebrew
  (`brew install oven-sh/bun/bun`), and needs `/opt/homebrew/bin` on `$PATH` explicitly in some
  shell invocations — the skill's own pinned-checksum curl installer failed a checksum match
  (bun.sh's install script had moved on from the pinned hash).
- Node/pnpm need `export PATH="$HOME/.nvm/versions/node/v23.11.0/bin:$PATH"` in some shell
  invocations — profile sourcing has been inconsistent across Bash tool calls at times.
- Local `.env` (gitignored, not the committed `.env.example`) is currently set to
  `AI_PROVIDER=docker-model-runner` / `AI_MODEL=ai/llama3.2:latest` on this machine, for my own
  demo/recording purposes — the committed default in `.env.example` correctly stays `mock`, per
  ADR-003's reasoning (a reviewer's first run must work with zero AI setup).
- `docs/Demo_Video_Script.md`/`.docx` exist on disk but are **not** tracked in git (in
  `.gitignore`) — they're a personal recording aid, not a submission deliverable. Don't be
  surprised they're missing on a fresh clone; that's intentional.

## Remaining

- **`docker compose up --build` still not empirically verified end to end** in this development
  environment — same root cause as before (severe local bandwidth), same mitigations already in
  place: `docker compose config` validates cleanly, both Dockerfiles were reviewed line-by-line,
  and the exact build commands each Dockerfile runs were verified natively with the resulting
  app extensively browser-tested. Documented honestly in the README rather than claimed as
  verified. If bandwidth allows in a future session, `docker compose up -d --build` (which would
  now also need to pick up the `V7` migration and the new `users` endpoints) is the next thing
  to actually finish and watch.

## Next recommended task

If bandwidth allows, attempt `docker compose up -d --build` and update the README's known-
limitations bullet either way (success, or another honest failure note). Otherwise the project
is complete: mark the submission (git tag `submission` or note the final commit SHA) and do a
final read through the README and the Technical Specification DOCX as if seeing them for the
first time.
