# Security review

A walk through the assessment's own checklist (authN, authZ, IDOR, injection, XSS, CSRF, SSRF,
prompt injection, secrets, logging, access control, mass assignment, deserialization,
dependencies, CORS, headers, rate limiting) against what's actually implemented, including
findings from an automated review during development and how each was fixed.

## Authentication

Stateless JWT (HS256), issued by `POST /api/v1/auth/login`, validated on every request by
`JwtAuthenticationFilter`. Passwords are bcrypt-hashed (`BCryptPasswordEncoder`); the login
endpoint returns an identical generic error whether the email doesn't exist or the password is
wrong, so a failed attempt never reveals which one was the problem.

`JWT_SECRET` has **no fallback default** in `application.yml` — this was an automated-review
finding during development (a default that lets the app boot "successfully" with a known,
public signing key is worse than refusing to start) and was fixed by making it a required
property. Same treatment for `POSTGRES_PASSWORD`.

**Token storage trade-off**: the frontend stores the access token in `localStorage`, not an
httpOnly cookie, because it's a pure client-side SPA with no Next.js server acting as a
backend-for-frontend to set a cookie on. This is a real, accepted trade-off (readable by any
script running on the page, i.e. an XSS vector) mitigated by a short default token lifetime (1
hour) and no refresh-token mechanism to extend that window. See
[Known limitations](../README.md#known-limitations).

## Account provisioning and lifecycle

`POST /api/v1/users` (ADMIN only) generates a 16-character password with `java.security.
SecureRandom` — never `Math.random()`, never client-supplied — returned exactly once in that
response and never logged; only its bcrypt hash is persisted. Self-service password change
(`PUT /api/v1/auth/me/password`) requires the current password, so a hijacked-but-not-yet-logged-
out session can't be used to silently and permanently lock the real owner out.

Accounts are disabled, never hard-deleted (`POST /api/v1/users/{id}/disable`/`enable`, ADMIN
only) — see [ADR-006](../.claude/decisions/ADR-006-user-management.md) for the foreign-key and
audit-trail reasons a real delete isn't offered. A disabled account is rejected at login
(`DaoAuthenticationProvider`'s built-in pre-authentication check, via `SecurityUser.isEnabled()`)
and, on every *subsequent* request, `JwtAuthenticationFilter` independently re-checks the same
flag with a fresh database read before honoring an otherwise-valid, unexpired JWT — this was a
real bug found during implementation: that filter builds its `Authentication` directly rather
than through `DaoAuthenticationProvider`, so without an explicit check of its own, a token issued
before an account was disabled would have kept working until natural expiry. Verified directly in
`UserApiTest.aDisabledUserCanNoLongerLogInOrUseAnExistingToken`, which disables an account
mid-session and asserts the same previously-valid token is rejected on the very next call.

An admin cannot disable their own account — enforced server-side
(`UserManagementService.disable`, `400` if the target ID matches the caller's own), preventing a
single-admin deployment from locking itself out with no recovery path.

## Authorization

Role-based (`ADMIN` / `VIEWER`), enforced **server-side** on every mutating endpoint via
`@PreAuthorize` at the service-method level — never only via frontend UI hiding. Verified
directly, not just asserted: `FeatureFlagApiTest` sends a real HTTP request as a VIEWER to an
ADMIN-only endpoint and asserts a real `403`.

Two authorization-adjacent bugs were found and fixed during development, both caught by that
same API-level test suite:

1. A request with **no** token at all to a protected endpoint returned a bare, empty-bodied
   `403` instead of `401` with the usual error body — Spring Security's path-level
   `authorizeHttpRequests` rejection happens at the filter level, before Spring MVC dispatch, so
   it never reached the app's normal exception handler. Fixed with a dedicated
   `RestAuthenticationEntryPoint` (401) and `RestAccessDeniedHandler` (403), both producing the
   same RFC 7807 shape as every other error response.
2. The correlation ID header was completely absent on that same rejected request, because the
   filter that sets it was registered (via Spring Boot's default `@Component` auto-registration)
   *after* Spring Security's own filter chain — so a request Security rejected outright never
   reached it. Fixed by wiring it directly into Security's own chain as the literal first thing
   it runs.

## IDOR (Insecure Direct Object References)

Every entity is looked up by UUID, and access is not scoped by "does this user own this row" —
by design, this is an internal team tool where `ADMIN`s manage flags across all environments and
`VIEWER`s can read everything. There is no per-user data partition to leak across in this
domain (contrast with Project 2's explicit multi-tenant IDOR concern). Roles gate *what actions*
are allowed, not *which rows* are visible.

## Injection

- **SQL injection**: Spring Data JPA repositories throughout — no string-concatenated queries
  anywhere in the codebase.
- **Prompt injection**: the AI system prompt explicitly instructs the model to treat the user's
  natural-language input as data to extract targeting criteria from, never as instructions to
  itself, and to ignore any attempt within it to override these instructions or claim to be a
  system message. AI output is additionally whitelist-validated (closed `operator` enum,
  plain-identifier `attribute` pattern, bounded `rolloutPercentage`) before it's ever shown to a
  user, let alone accepted as input to a mutation.
- **Log injection / response splitting**: the `X-Correlation-ID` request header is untrusted
  client input reflected onto the response and written into every log line for the request. An
  automated review caught that the original implementation passed it through unvalidated —
  fixed with a strict allowlist pattern (`^[A-Za-z0-9_-]{1,100}$`); anything else is discarded in
  favor of a freshly generated UUID. Regression-tested with a literal CRLF/`Set-Cookie`
  injection payload in `CorrelationIdFilterTest`.

## XSS

React escapes all rendered text by default; no `dangerouslySetInnerHTML` anywhere in the
frontend. AI-generated explanation text is rendered as plain text, not interpreted as
HTML/Markdown.

## CSRF

Not applicable by design: the API is a stateless Bearer-token API with no cookie-based session
for CSRF to exploit. `csrf().disable()` in `SecurityConfig` is a deliberate, documented choice
for this auth model, not an oversight.

## SSRF

`DockerModelRunnerAiProvider` calls a single, operator-configured URL (`app.ai.base-url`) — never
a user-supplied one. No feature in this application accepts an arbitrary URL from a user and
fetches it.

## Secrets hygiene

`.env` is gitignored; `.env.example` documents every variable with safe placeholders and no real
values. `JWT_SECRET` and `POSTGRES_PASSWORD` are required properties with no insecure fallback
(see Authentication above). Demo account credentials (`Password123!`) are intentionally public —
seed data for a take-home assessment, not a production secret — and documented as such in the
README rather than hidden.

## Insecure logging

`GlobalExceptionHandler`'s catch-all handler logs the full exception server-side (with the
correlation ID) but returns only a generic message and the correlation ID to the client — never
a stack trace, SQL error text, or exception class name to the caller. Verified directly in
`FeatureFlagApiTest.loginWithWrongPasswordIs401NotAStackTrace`.

## Mass assignment

Request DTOs (Java records with Bean Validation) are explicit allowlists of exactly the fields a
client may set — there is no path from an arbitrary JSON body to setting fields like `version`,
`createdBy`, or `id` directly. JPA entities are never exposed over the API; every response is
mapped to a DTO.

## Unsafe deserialization

No custom `ObjectInputStream`/Java serialization anywhere — all (de)serialization is JSON via
Jackson, with DTOs as explicit target types (no `Object`/`Map<String,Object>` catch-alls for
untrusted input).

## Dependency supply chain

All backend dependencies pinned to specific versions in `pom.xml` (Spring Boot's dependency
management BOM pins the rest); all frontend dependencies pinned via `pnpm-lock.yaml`. No use of
`:latest` Docker base image tags — `eclipse-temurin:21-jre-alpine`, `node:22-alpine`,
`postgres:17-alpine`, `redis:8-alpine` are all explicit versions.

## CORS

`CorsConfiguration` scoped to `app.cors.allowed-origins` (defaults to the frontend's own origin
only, `http://localhost:3000`) — not a wildcard `*`.

## Security headers

Spring Security's defaults are active (`X-Content-Type-Options: nosniff`,
`X-Frame-Options: DENY`, cache-control headers on API responses) — visible directly in the
`FeatureFlagApiTest` response header assertions and in manual smoke-test output.

## Rate limiting

**Not implemented.** Documented as a real gap rather than silently omitted — see
[Production readiness](../README.md#production-readiness) in the README for where this would be
added before real production traffic (`/api/v1/auth/login` at minimum, for brute-force
resistance).
