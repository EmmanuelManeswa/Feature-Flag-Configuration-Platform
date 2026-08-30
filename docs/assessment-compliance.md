# Assessment compliance checklist

Mapped against the actual assessment PDF (`docs/Senior_Software_Developer_Take_Home_Assessment.pdf`,
Project 1 and the general requirements in sections 3–5), not the earlier expanded ChatGPT draft —
the PDF is the authoritative source per `.claude/CLAUDE.md`. Nothing below is marked done unless
it's actually implemented, tested, and runnable.

## Project 1 minimum functional scope

| Requirement | Implemented | Tested | Documented |
| --- | :---: | :---: | :---: |
| APIs to manage flags and environments | ✅ | ✅ | ✅ Swagger + README |
| Evaluate flags for a supplied user context | ✅ | ✅ | ✅ README "AI integration" sibling section + ADR-001 |
| Immutable audit trail | ✅ | ✅ | ✅ ADR referenced in README, `AppendOnlyRepository` |
| BOOLEAN strategy | ✅ | ✅ | ✅ |
| PERCENTAGE_ROLLOUT strategy | ✅ | ✅ | ✅ ADR-001 |
| Caching with explicit invalidation | ✅ | ✅ (Redis-down resilience manually verified) | ✅ ADR-002 |
| Admin dashboard | ✅ | manual (browser) | ✅ screenshots described in commit messages |
| Create/edit screens | ✅ | ✅ unit + manual | ✅ |
| Audit-log view | ✅ | ✅ | ✅ |
| Evaluation playground | ✅ | ✅ unit + manual | ✅ called out as strongest demo feature per assessment |
| AI rule assistant (propose only, human confirms) | ✅ | ✅ (15 backend + 3 frontend tests) | ✅ README "AI integration", ADR-003 |

## Required implementation details (Project 1)

| Requirement | Status |
| --- | --- |
| Evaluation endpoint designed for low latency; explain how to keep it performant at scale | ✅ Cache-aside on Redis keyed by flag ID (skips Postgres entirely on a hit); scaling discussion in `docs/production-readiness.md` |
| No random-per-request percentage assignment | ✅ Deterministic SHA-256 hash — ADR-001 |
| Every config change creates an audit record with actor/timestamp/previous/new value | ✅ `AuditService.record`, called in the same transaction as every mutation |
| Concurrency/stale-update protection (version column, ETag, optimistic locking) | ✅ `@Version` + proactive `expectedVersion` check → 409, verified against a real concurrent-edit scenario (see `feat(frontend): feature flags...` commit) |

## General requirements (all projects, assessment section 4)

| Requirement | Status |
| --- | --- |
| Real authentication | ✅ JWT, bcrypt-hashed passwords |
| Authorization protecting at least one operation by role | ✅ Every mutating endpoint is `ADMIN`-only via `@PreAuthorize`, verified with a real `403` in `FeatureFlagApiTest` |
| Backend validation + useful frontend feedback | ✅ Bean Validation + domain checks server-side, Zod + TanStack Form client-side |
| Consistent API errors, no raw stack traces | ✅ RFC 7807 `ProblemDetail` everywhere, verified no stack trace/exception name leaks in `FeatureFlagApiTest` |
| Data survives restarts | ✅ Postgres + Redis named Docker volumes |
| Sensible REST design, pagination | ✅ `GET /api/v1/flags`, `/audit-logs` are paginated |
| Structured logs, correlation/request IDs, metrics | ✅ `CorrelationIdFilter`, JSON logs in docker/prod, Micrometer counters/timer |
| No committed secrets; common risk awareness (injection, IDOR, SSRF, prompt injection) | ✅ See `docs/security.md` for the full walkthrough |
| Automated tests: important business logic + API/integration tests | ✅ 50 backend tests (unit/integration/API), 13 frontend tests |
| Usable UI with loading/empty/error/success states | ✅ Skeletons, empty-state copy, toasts, disabled-while-pending buttons throughout |

## Mandatory deliverables (assessment section 8)

| Deliverable | Status |
| --- | --- |
| Working backend service | ✅ |
| Working frontend integrated with the backend | ✅ (verified via real browser testing end-to-end, not just type-checked) |
| Database schema and migrations | ✅ Flyway `V1`–`V6` |
| Project-specific AI feature | ✅ |
| Automated tests | ✅ |
| README meeting the stated requirements | ✅ |
| API documentation (Swagger/OpenAPI) | ✅ `/swagger-ui.html` |
| Docker-based run configuration | ✅ `docker-compose.yml` + Dockerfiles for both services |
| 5–10 minute demo covering main flow, one failure path, AI feature, tests | Candidate to record — see suggested flow below |

## Suggested demo flow (assessment section 9's review questions)

1. Log in as `admin@example.com`, show the dashboard.
2. Open Feature Flags, create a `PERCENTAGE_ROLLOUT` flag with a targeting rule.
3. Open the flag, run the evaluation playground for two different `stableIdentifier` values —
   show the bucket stays consistent on repeat calls.
4. Edit the flag, show the version bump and the audit diff.
5. Demonstrate the stale-version conflict: open edit, update the same flag from a second
   tab/`curl` call, submit the first edit, show the 409 message.
6. Open the AI Rule Assistant, type the assessment's own example sentence, show the labeled
   proposal, apply it, show it pre-fills the create form untouched until submitted.
7. **Failure path**: `docker compose stop redis`, evaluate a flag again, show it still resolves
   correctly (just slower) via the Postgres fallback — this is the assessment's explicitly
   requested "show what happens when a dependency fails" moment.
8. Run `./mvnw test` and `pnpm test`, show both suites passing.
9. Open Swagger UI, show the AI-unavailable `503` documented on the AI endpoint.

## Honest gaps

See the README's [Known limitations](../README.md#known-limitations) section — dashboard
aggregation is client-side, no server-side flag text search, environments have no
update/delete, and the token-storage/no-rate-limiting trade-offs are documented there rather
than glossed over.
