# Current State

Last updated: 2026-08-28 (session 1)

## Completed
- Read the authoritative assessment PDF (`docs/Senior_Software_Developer_Take_Home_Assessment.pdf`).
- Scope calibration decided with candidate: **lean & rubric-optimized**, **autonomous phase-by-phase
  commits** (see `CLAUDE.md` for what "lean" means concretely).
- Confirmed local environment: Java 21 (Temurin), Node 23, pnpm 10, Docker 29 + Compose v5,
  **Docker Model Runner is running** (llama.cpp backend, no models pulled yet — need to pull one).
  No system Maven — using the Maven wrapper instead.
- `.claude/` docs scaffolded (this file, CLAUDE.md, implementation-plan.md, decisions/).
- `.gitignore` written (env, Java/Maven, Node/Next, IDE, OS, docker-data).

## Current work
- Phase 1: scaffolding (backend Maven project, frontend Next.js project, docker-compose skeleton).

## Remaining
- See `implementation-plan.md` phases 2–18.

## Known bugs
- None yet (nothing built).

## Tests added / failing
- None yet.

## Architectural decisions made
- See `decisions/` — ADR-001 (evaluation algorithm), ADR-002 (caching), ADR-003 (AI provider
  abstraction) to be written alongside their phases.

## Next recommended task
- Finish Phase 1 scaffolding, then Phase 2 (Flyway migrations).
