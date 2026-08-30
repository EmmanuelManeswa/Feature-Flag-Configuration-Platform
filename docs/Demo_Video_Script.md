---
title: "Feature Flag & Configuration Platform — Demo Video Script"
author: "Emmanuel Maneswa"
date: "2026-08-31"
---

# How to use this document

This is a full, narrated walkthrough script covering **every** operation available in the
application, plus a codebase tour — more material than the assessment's own 5-10 minute demo
requirement asks for. That's deliberate: this document is the complete reference to record from,
and you cut it down to time rather than trying to remember everything live.

**If you only have 5-10 minutes** (the assessment's actual requirement), record just the
sections marked **[CORE]** below and skip the rest — that alone covers the assessment's explicit
checklist: main flow, one failure path, the AI feature, and the automated tests. Everything else
(the deeper codebase tour, every stretch goal, every secondary flow) is here so you have it ready
if you want a longer, more thorough recording for a portfolio, or if a reviewer asks for more
depth than the short cut covers.

Each section gives: **what to show on screen**, **what to say** (a starting point, not a
word-for-word script to read robotically), and an **approximate duration**.

## Pre-recording checklist

- [ ] Backend running with fresh seed data (`docker compose down -v && docker compose up --build`,
      or native: drop and recreate the local Postgres database, then `./mvnw spring-boot:run` —
      Flyway reseeds automatically). Fresh seed data matters because a couple of segments below
      reference the exact seeded flags (`advanced-search`, `ai-assistant`) by their original
      values.
- [ ] Frontend running (`pnpm dev` or via Docker), reachable at `http://localhost:3000`.
- [ ] Swagger UI reachable at `http://localhost:8080/swagger-ui.html`.
- [ ] Two browser windows (or one window + one private/incognito window) logged in as
      `admin@example.com` and `viewer@example.com` respectively, for the RBAC and live-update
      segments.
- [ ] A terminal ready in the repo root, and a second one in `examples/sdk-client/`.
- [ ] If demonstrating the live AI path: Docker Model Runner running with `ai/llama3.2` already
      pulled *and warmed up* (make one throwaway AI request before recording — the first call
      after the model has been idle takes 10-20 seconds to load, which is real and documented
      behavior, not a bug, but not what you want as the first thing on camera). Otherwise, the
      default `MockAiProvider` path is equally legitimate to demo and needs no warm-up.
- [ ] Screen resolution and zoom level such that both the Swagger UI and the app's own UI are
      comfortably readable in a recording.

---

# Part A — Codebase Tour

*(Optional deeper-dive material — skip for the 5-10 minute cut. Show these in an editor with the
repository open, not the running application.)*

## A.1 Orientation (1 min)

**Show:** the repository root, then `README.md`'s table of contents.

**Say:** "This is a feature flag and configuration platform — built as a senior developer
take-home assessment. Before touching code, I want to establish scope: the assessment PDF itself
was the authoritative requirements source, not a much larger draft I'd prepared independently
beforehand. The README documents that decision, along with every architectural choice, as
Architecture Decision Records — five of them, in `.claude/decisions/`."

## A.2 The evaluation engine (2 min)

**Show:** `backend/src/main/java/com/featureflagplatform/evaluation/domain/`, specifically
`FeatureFlagEvaluator.java` and `FeatureFlagEvaluatorTest.java` side by side.

**Say:** "This is the highest-value code in the whole project — every feature check any consuming
application makes ultimately runs through here. It's deliberately framework-free: no Spring, no
HTTP, no JPA anywhere in this package. That's not an accident, it's what makes it trivially unit
testable — 18 tests, including golden-vector cross-checks against an independent Python
implementation of the same hashing algorithm, specifically to catch a subtle bug a same-language
test suite might share a blind spot on.

"The core rule: percentage rollout is `SHA-256(flagKey:environment:stableIdentifier) mod 100` —
never `Math.random()`. That's what makes a 50% rollout mean something coherent: the same user is
always on the same side of the line, every request, forever, until the flag itself changes."

## A.3 Package-by-feature structure (1 min)

**Show:** `backend/src/main/java/com/featureflagplatform/` folder tree.

**Say:** "The backend is organized by feature — `featureflag`, `environment`, `audit`,
`evaluation`, `ai`, `auth`, `common` — not by layer. Everything about one domain concept lives
together. Within each feature, the normal layering still holds: controllers depend on services,
services depend on repositories, and JPA entities never cross the API boundary directly — every
response is an explicit DTO."

## A.4 Caching and optimistic concurrency (1.5 min)

**Show:** `FeatureFlagService.java`'s `update` method, and `ADR-002-caching-strategy.md`.

**Say:** "Two things worth pointing at here. First, optimistic concurrency — every update carries
the version it was loaded at; if that's stale, it's a `409`, not a silent overwrite. Second, the
comment right here" *(point at the `saveAndFlush`, not `save`, comment)* "is a real bug I found by
actually running this application, not by reading the code carefully enough in advance — `save()`
alone defers the version increment to end-of-transaction, so the API response and the audit
record were both reporting the pre-update version. That's the kind of bug that only shows up when
you actually exercise the running system."

## A.5 The AI provider abstraction (2 min)

**Show:** `ai/provider/AiProvider.java`, `MockAiProvider.java`, `DockerModelRunnerAiProvider.java`,
and `AiRuleAssistantService.java`'s validation pipeline.

**Say:** "The assessment is explicit that trusting AI output without validation is an automatic
deduction. Here's how that's enforced structurally, not just by policy: `RuleProposalDto`'s
`rules` field is typed as the *exact same* `TargetingRuleDto` a human-submitted flag's targeting
rules are validated as. There's no separate, looser validation path for AI output — it's
literally the same type going through the same Bean Validation and the same domain checks.

"There are two providers behind one narrow interface: `MockAiProvider`, the default — a real
deterministic parser, not a stub — and `DockerModelRunnerAiProvider`, which calls a real local
LLM with no API key and no per-request cost. Swapping between them is one environment variable."

## A.6 Test suites (1.5 min)

**Show:** terminal, run `./mvnw test` (backend) and `pnpm test` (frontend) side by side, or
sequentially.

**Say:** "58 backend tests, 19 frontend tests. The backend suite mixes pure unit tests, real
Postgres/Redis integration tests via Testcontainers — deliberately not mocked, because the bug I
just showed you only reproduces against a real Hibernate flush cycle — and full HTTP tests through
MockMvc for auth, authorization, and error shapes."

---

# Part B — Live Application Walkthrough

*(This is the application itself, running in a browser. **[CORE]** marks the assessment's
required 5-10 minute cut.)*

## B.1 Login and dashboard **[CORE]** (30s)

**Show:** `http://localhost:3000/login`, log in as `admin@example.com` / `Password123!`.

**Say:** "Logging in as the admin account. Real authentication — JWT, bcrypt-hashed passwords in
the database, not a mock login. The dashboard gives an at-a-glance view: total flags, how many
enabled/disabled, how many are percentage rollouts, and recent audit activity."

## B.2 Environments (30s)

**Show:** the Environments page — DEV, STAGING, PROD.

**Say:** "Flags are scoped to environments — the same flag key can have completely different
values in DEV versus PROD, which is exactly what you'd want for testing a rollout safely before
it reaches production traffic."

## B.3 Create a feature flag **[CORE]** (1.5 min)

**Show:** Feature Flags → Create flag. Fill in: key `checkout-redesign`, name "Checkout
Redesign", environment DEV, type `PERCENTAGE_ROLLOUT`, rollout 30%, add a targeting rule
(`location` `NOT_EQUALS` `internal`). Submit.

**Say:** "Creating a flag with a 30% rollout and one targeting rule — this excludes anyone whose
`location` attribute equals `internal`. Notice the form validates client-side with Zod, mirroring
the backend's own Bean Validation, but the backend is always the actual authority — nothing here
is trusted without a server-side round trip."

## B.4 Evaluation playground **[CORE]** (2 min)

**Show:** open the new flag's detail page, use the evaluation playground with two or three
different `stableIdentifier` values (e.g. `user-1`, `user-2`, `user-3`), running each twice.

**Say:** "This is the strongest single demo feature the assessment itself calls out. I plug in a
stable identifier — a real user ID, not a session token — and see exactly why the flag resolved
the way it did: the bucket it landed in, whether a targeting rule excluded it first. Running the
same identifier twice" *(re-run `user-1`)* "gives the identical result both times — that's the
deterministic-hash guarantee, not a coin flip."

## B.5 Edit, version bump, and audit diff **[CORE]** (1.5 min)

**Show:** edit the flag (change rollout to 50%), submit, then open the Audit history tab and
click "View diff" on the resulting UPDATE entry.

**Say:** "Editing bumps the version and writes an immutable audit row in the same transaction —
either both happen or neither does. The diff view shows exactly what changed: rollout percentage
10 to 50, in this case, who did it, and when."

## B.6 Stale-version conflict **[CORE]** (2 min)

**Show:** open the edit dialog for the same flag in the browser (don't submit yet); in a terminal,
`curl` a `PUT` to the same flag with the *current* version to make an out-of-band change; then
submit the still-open browser edit dialog.

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"Password123!"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -s -X PUT http://localhost:8080/api/v1/flags/<flag-id> \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Checkout Redesign","enabled":true,"rolloutPercentage":75,"targetingRules":[],"expectedVersion":<current-version>}'
```

**Say:** "This is the concurrent-edit scenario the assessment specifically asks for. I have the
edit dialog open with the version I originally loaded; meanwhile, someone else — simulated here
with a direct API call — changes the same flag first. When I submit my now-stale edit, the
backend rejects it with a `409`, and the frontend surfaces that explicitly rather than silently
overwriting the other person's change or silently discarding mine."

## B.7 AI Rule Assistant **[CORE]** (2.5 min)

**Show:** open the AI Rule Assistant, type the assessment's own canonical example: "enable this
for 20% of users in Harare except internal staff." Submit, show the labeled proposal (strategy,
percentage, rules, explanation), click Apply, show it pre-fills the create-flag form — untouched
until explicitly submitted.

**Say:** "This is the assessment's own example sentence. The assistant proposes a structured
rule — 20% rollout, a targeting rule for Harare, an exclusion for internal staff — with an
explanation of its reasoning. Critically: applying this only pre-fills the create form. Nothing
is saved yet. I still have to review it and explicitly submit, going through the exact same
validation, audit trail, and optimistic-concurrency path as any manually-authored flag. There is
no code path from 'the model said so' to a saved flag."

*(If demonstrating the failure path too: stop Docker Model Runner, or temporarily set
`AI_PROVIDER` to a bad value, and show the `503` — "Unable to generate a rule proposal right now.
You can configure the rule manually." — with no leaked internal detail.)*

## B.8 Role-based access control (1.5 min)

**Show:** in a second browser window, log in as `viewer@example.com`. Show the Create/Edit/Delete
buttons are absent. Then attempt the same mutating call via `curl` with the viewer's token,
showing a real `403`.

```bash
VIEWER_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"viewer@example.com","password":"Password123!"}' | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -i -X POST http://localhost:8080/api/v1/flags \
  -H "Content-Type: application/json" -H "Authorization: Bearer $VIEWER_TOKEN" \
  -d '{"key":"viewer-attempt","name":"x","environmentId":"<any-id>","type":"BOOLEAN","enabled":true}'
```

**Say:** "The frontend hides these buttons for a VIEWER, but that's a UX nicety, not the security
boundary. Calling the same endpoint directly with a viewer's token still gets a real `403` — the
authorization check is server-side, on every mutating endpoint, with no exceptions."

## B.9 Failure path: Redis down **[CORE]** (2 min)

**Show:** stop Redis (`docker compose stop redis`, or kill the native process), re-run the
evaluation playground on the same flag, show it still resolves correctly. Optionally show the
response's `cacheHit: false` and slightly higher latency figure. Restart Redis afterward.

**Say:** "This is the failure path the assessment explicitly asks to see. Redis is the evaluation
cache, not the source of truth — Postgres is. With Redis stopped, the exact same evaluation call
still returns the correct result; it's just a little slower, because it fell back to Postgres.
The request never fails — that's the whole point of the cache-aside design."

## B.10 Automated tests **[CORE]** (1 min)

**Show:** terminal, `./mvnw test` and `pnpm test`, both passing.

**Say:** "58 backend tests, 19 frontend tests, all passing — unit tests on the evaluation engine
and AI validation pipeline, real-Postgres/Redis integration tests, full HTTP API tests, and
component tests on the frontend's more complex interactive states."

## B.11 Swagger / API documentation (1 min)

**Show:** `http://localhost:8080/swagger-ui.html`, click Authorize, paste a token, expand the AI
endpoint to show the documented `503` response.

**Say:** "Every endpoint here documents every status code it can actually return, including the
specific error shape — this isn't generated boilerplate, every response was written and verified
against what the API genuinely does. Authorize once, and every protected endpoint is directly
testable from this page."

## B.12 Dark mode (15s)

**Show:** toggle the theme switch in the topbar.

**Say:** "Full light/dark theming, driven by CSS variables — a quick thing to show, not load-
bearing, but it's there."

## B.13 Stretch goal: live flag-change updates (2 min)

**Show:** two browser windows/tabs side by side, both on the Feature Flags list page. Point out
the "Live" indicator (green dot) in the topbar of each. In one, edit a flag (or run the `curl`
`PUT` from B.6 again) — show the *other* window's list update the changed row with no manual
refresh.

**Say:** "This is one of the optional stretch goals — Server-Sent Events, not WebSocket, because
the traffic here is genuinely one-way: a flag changed, go refetch. Watch this tab — I'm not going
to touch it — while I edit the same flag from this other window. No refresh, no polling interval
elapsing — the update lands the moment the change commits on the backend."

## B.14 Stretch goal: evaluation metrics (1 min)

**Show:** a flag detail page's "Evaluation metrics" card, after running a few evaluations in the
playground first so there's a non-zero count to show.

**Say:** "Another stretch goal — basic evaluation metrics per flag, broken down by result. This
reads the same counters the platform already collects for its Prometheus metrics endpoint, just
shaped and scoped per flag here instead of buried in raw metrics text."

## B.15 Stretch goal: sample SDK client (1.5 min)

**Show:** terminal, `cd examples/sdk-client && node example.mjs`, let it run to completion.

**Say:** "The third stretch goal — a minimal, dependency-free Node client showing how an actual
application would consume this API: log in once, evaluate a real rollout flag for several
different users, and print the results. Watch — the same identifiers produce the identical
true/false pattern every time this runs, which is external, outside-the-codebase proof the
rollout algorithm really is deterministic. It also reads the metrics endpoint and briefly
subscribes to the live-update stream, printing whatever event arrives."

## B.16 Delete a flag (30s)

**Show:** delete the demo flag created in B.3, confirm the dialog, show it's gone from the list
and the DELETE entry now appears in the global audit log with the flag's last known
configuration preserved.

**Say:** "Deleting a flag still records an immutable audit entry with its last known state before
removal — the audit trail never loses that history, even for a deleted flag."

---

# Closing (30s)

**Say:** "That's the full platform — feature flags with deterministic rollouts, an immutable
audit trail, optimistic concurrency, an AI assistant that proposes but never applies, and all
three optional stretch goals: live updates, evaluation metrics, and a sample SDK client. Every
piece of this was actually run and exercised, not just written and assumed to work — several real
bugs in this project were only caught that way, and that practice is documented in the README's
own AI-assisted-development disclosure section, alongside every architectural trade-off made
along the way."

---

# Suggested recording order for the 5-10 minute submission cut

Record only the **[CORE]**-marked sections, in this order, for a demo that hits the assessment's
own checklist (main flow, a failure path, the AI feature, and the tests) inside roughly 8-9
minutes:

B.1 → B.3 → B.4 → B.5 → B.6 → B.7 → B.9 → B.10

Everything else in this script — the codebase tour, RBAC, Swagger, dark mode, and all three
stretch goals — is available as a longer, fully-narrated recording if you want to produce one for
a portfolio beyond the assessment's minimum ask.
