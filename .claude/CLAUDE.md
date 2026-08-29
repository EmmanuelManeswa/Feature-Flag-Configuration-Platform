# CLAUDE.md — Feature Flag & Configuration Platform

Persistent project instructions. Read this, `current-state.md`, and `implementation-plan.md`
at the start of every session, in that order, before touching code.

## Project mission

A feature flag and configuration platform that lets engineering teams turn features on/off
and roll them out gradually, per environment, without redeploying. Built as a senior
software developer take-home assessment (`docs/Senior_Software_Developer_Take_Home_Assessment.pdf`,
Project 1). **That PDF is the authoritative requirements source** — not the earlier ChatGPT-drafted
mega-prompt the candidate pasted alongside it. The ChatGPT draft is useful as a menu of
technology ideas but overshoots the assessment's own guidance: *"Build only the features
required for a convincing working vertical slice... a large but fragile implementation"*
scores worse than a focused one.

**Scope calibration the candidate chose (2026-08-28): "Lean & rubric-optimized."** Prefer
fewer, well-tested, well-explained things over maximal library coverage. Concretely this means:
- TanStack Query + Table + Form only on the frontend (genuine value each). No TanStack Pacer,
  Charts, Markdown, or AI — they weren't earning their complexity budget for this scope.
- Slim `.claude/` docs: this file, `current-state.md`, `implementation-plan.md`, and a small
  number of ADRs for decisions that are actually load-bearing (evaluation algorithm, caching/
  invalidation, AI provider abstraction, optimistic concurrency). No `session-notes/` ceremony.

## Technology stack (authoritative)

- **Backend**: Java 21, Spring Boot 3.x, Spring Web/Validation/Security/Data JPA, PostgreSQL,
  Flyway, Redis (Lettuce via Spring Data Redis), Actuator, springdoc-openapi, Micrometer,
  JUnit 5, Mockito, Testcontainers. Maven (wrapper committed, no system Maven required to build).
- **Frontend**: Next.js (App Router) + TypeScript, Tailwind CSS, shadcn/ui, TanStack Query,
  TanStack Table, TanStack Form + Zod, next-themes, Lucide icons. pnpm.
- **AI**: provider abstraction (`AiRuleAssistant` / `AiProvider`) with two implementations —
  `DockerModelRunnerAiProvider` (OpenAI-compatible endpoint, local model, no API key, no cost)
  and `MockAiProvider` (deterministic canned responses, used as the safety-net default and in
  tests). Never a direct, un-abstracted LLM call from a controller/component.
- **Auth**: JWT access tokens via Spring Security, roles `ADMIN` / `VIEWER`.

## Architecture rules

- **Backend**: package-by-feature (`featureflag/`, `environment/`, `audit/`, `evaluation/`,
  `ai/`, `auth/`, `common/`), not package-by-layer. Controllers depend on services; services
  depend on repositories; never expose JPA entities directly — map to DTOs (Java records).
  Transaction boundaries live in the service layer and are explicit (`@Transactional`).
- **Evaluation engine is framework-free.** `EvaluationContext`, `FeatureFlagSnapshot`,
  `TargetingRule`, `FeatureFlagEvaluator` know nothing about HTTP, Redis, JPA, or Spring.
  This is the highest-value code in the whole project — it must be trivially unit-testable
  and must stay that way.
- **Caching**: business logic depends on the `FeatureFlagCache` interface, never on
  `RedisTemplate` directly. Redis failures degrade to Postgres, they never fail the request.
  See `decisions/ADR-002-caching-strategy.md`.
- **AI**: the domain/application layer depends on `AiRuleAssistant`, never on a concrete
  provider. AI output is always a `RuleProposal` DTO that passes through the same
  Bean Validation + domain validation as a human-submitted rule before it can be persisted.
  Nothing the AI returns is ever saved without an explicit, separate human "apply" action.
- **Frontend**: `features/<domain>/` colocates hooks, components, and schemas per domain;
  `components/ui/` is shadcn primitives only. TanStack Query owns all server state — no
  ad-hoc `useEffect` fetching. Zod schemas mirror backend validation for fast feedback, but
  the backend is always the authority.

## Non-negotiable rules

1. Never expose secrets (API keys, JWT signing key, DB password) in logs, prompts, or the repo.
2. Never let AI output bypass validation — schema, then domain rules, then human confirmation.
3. Never expose JPA entities directly over the API — always map to DTOs.
4. Never use `random()` for percentage rollout — must be a deterministic hash of
   `flagKey:environment:stableIdentifier`. See `decisions/ADR-001-evaluation-algorithm.md`.
5. Never bypass authorization — every mutating endpoint checks role server-side; the frontend
   hiding a button is a UX nicety, not a security boundary.
6. Never mutate or delete an audit log row.
7. Never silently overwrite a stale version — optimistic locking returns 409 with a structured
   Problem Details body, and the frontend surfaces it explicitly.
8. Never leave a broken test or a TODO in core functionality (evaluation, auth, audit, caching).
9. Update `current-state.md` and the relevant ADR whenever an architectural decision changes.

## Coding standards

- Java records for DTOs; constructor injection everywhere; no field injection.
- Bean Validation (`@NotBlank`, `@Min`/`@Max`, etc.) on request DTOs; domain invariants
  (e.g. "rollout percentage requires PERCENTAGE_ROLLOUT strategy") enforced in the service layer.
- Consistent error shape: RFC 7807 Problem Details for every 4xx/5xx, including a correlation ID.
- No `utils/`/`helpers/`/`misc/` grab-bag packages unless something is genuinely generic.
- Commit in small, meaningful units (see `git log` for the running history) — never one mega-commit.
