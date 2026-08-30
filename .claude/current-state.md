# Current State

Last updated: 2026-08-31 (session 2 — stretch goals, live AI verification, documentation
deliverables)

## Completed — full lean-scope implementation plus all three stretch goals

**Backend** (Spring Boot 3.5.16, 58 tests passing):
- Evaluation engine (framework-free), deterministic SHA-256 bucketing — ADR-001
- Auth: JWT, Spring Security, ADMIN/VIEWER roles
- Environments API (create/list/get)
- Feature Flags API: full CRUD, optimistic concurrency (409), evaluate, per-flag audit
- Redis caching (cache-aside, keyed by flag ID) — ADR-002, verified resilient to Redis being
  down by hand (36ms fallback after tuning the Lettuce timeout down from 2s)
- Immutable audit trail (`AppendOnlyRepository` — no delete method exists in its hierarchy)
- AI rule assistant: `AiProvider`/`AiRuleAssistantService` abstraction, `MockAiProvider`
  (default) + `DockerModelRunnerAiProvider` — ADR-003, **now live-verified against a real
  pulled `ai/llama3.2` model** (see below)
- Observability: correlation IDs (wired ahead of Spring Security's own chain), RFC 7807 errors
  everywhere including filter-level 401/403, structured JSON logs (docker/prod profile),
  Micrometer counters/timer, Actuator
- Full OpenAPI/Swagger docs per endpoint per status code, bearer-auth wired into Swagger UI
- **Stretch goal — live updates**: `GET /api/v1/flags/stream` (SSE), `FlagChangeNotifier`
  broadcasting only `AFTER_COMMIT` — ADR-005
- **Stretch goal — evaluation metrics**: `GET /api/v1/flags/{id}/metrics`, reads the existing
  Micrometer counters back scoped/grouped per flag — ADR-005

**Frontend** (Next.js 16 / React 19, 19 tests passing):
- shadcn/ui (Radix base) + custom indigo/violet brand theme (light/dark via next-themes)
- Auth (JWT in localStorage, documented trade-off), protected app shell, sidebar/topbar
- Dashboard, Feature Flags list+create+edit (TanStack Table/Form/Query), flag detail page
- Evaluation playground, AI Rule Assistant dialog (apply → pre-fills create form, never
  auto-saves), Audit Log (global + per-flag, with a curated diff view), Environments
- Vitest + RTL tests for the states that actually matter: evaluation result rendering, AI
  proposal review (including the AI-unavailable path), 409 stale-version conflict, DataTable
  loading/empty states, hand-rolled SSE event parser
- **Stretch goal — live updates UI**: `lib/sse-client.ts` (fetch + ReadableStream, not
  `EventSource` — see ADR-005 for why), `LiveUpdatesIndicator` in the topbar, flags list/
  dashboard auto-refresh on any flag change from any source
- **Stretch goal — metrics UI**: `FlagMetricsCard` on the flag detail page

**Stretch goal — sample SDK client**: `examples/sdk-client/` — plain Node.js, zero dependencies,
`client.mjs` + a runnable `example.mjs` demo. Live-verified twice against the real backend,
confirming identical deterministic-rollout results both runs.

**Docker**: multi-stage Dockerfiles for both services (non-root, healthchecks), full
`docker-compose.yml` (postgres/redis/backend/frontend), named volumes for persistence.

**Docs**: README (all required sections plus "Stretch goals", "Switching the AI model", "Data &
log persistence"), `docs/security.md`, `docs/production-readiness.md`,
`docs/assessment-compliance.md`, ADR-001 through ADR-005.

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
  `AI_PROVIDER=docker-model-runner` / `AI_MODEL=ai/llama3.2:latest` on this machine, for the
  candidate's own demo/recording purposes — the committed default in `.env.example` correctly
  stays `mock`, per ADR-003's reasoning (a reviewer's first run must work with zero AI setup).

## Remaining

- **`docker compose up --build` still not empirically verified end to end** in this development
  environment — checked again this session (`docker images | grep feature-flag` → nothing built
  yet, only postgres/redis containers were up from native-mode development). Same root cause as
  before (severe local bandwidth), same mitigations already in place: `docker compose config`
  validates cleanly, both Dockerfiles were reviewed line-by-line, and the exact build commands
  each Dockerfile runs were verified natively with the resulting app extensively browser-tested.
  Documented honestly in the README rather than claimed as verified. If bandwidth allows in a
  future session, `docker compose up -d --build` is the next thing to actually finish and watch.

Both DOCX deliverables (technical specification, demo video script) are done — see
`docs/Technical_Specification.md`/`.docx` and `docs/Demo_Video_Script.md`/`.docx`.

## Next recommended task

If bandwidth allows, attempt `docker compose up -d --build` and update the README's known-
limitations bullet either way (success, or another honest failure note). Otherwise the project
is complete: mark the submission (git tag `submission` or note the final commit SHA) and do a
final read through the README and both DOCX deliverables as if seeing them for the first time.
