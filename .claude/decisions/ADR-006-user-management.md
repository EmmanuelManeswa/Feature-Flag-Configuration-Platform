# ADR-006: Admin user management — generated passwords, disable not delete

**Status:** Accepted

## Context

Added after the core assessment scope: an ADMIN needs a way to provision accounts for other
people to use in a demo or review session, without a real signup flow. Three concrete
requirements shaped this: the backend generates the password (never client-supplied) so it can be
copied and shared out of band; an admin must not be able to lock the system out by disabling
their own account; and self-service password change for whoever is logged in, regardless of role.

## Password generation: `SecureRandom`, never client-supplied

**Decision:** `POST /api/v1/users` takes no password field at all. `PasswordGenerator` produces a
16-character password (`java.security.SecureRandom`, never `Math.random()` — the same "never a
predictable source" bar this project already holds the rollout hash to, see ADR-001) with at
least one uppercase letter, one lowercase letter, one digit, and one symbol, drawn from an
alphabet that deliberately excludes visually ambiguous characters (`I`/`O`/`l`/`0`/`1`) since this
password is read off a screen and typed or copied by a human. Only the bcrypt hash is persisted;
the plaintext is returned exactly once, in the HTTP response to the create call, and never
logged.

**Why not let the admin choose the initial password?** A human-chosen password for someone else's
account is either weak (predictable, reused) or the admin has to invent and separately transmit a
strong one anyway — generating it server-side removes a weak-password path entirely and matches
how the assessment's own demo accounts work (a fixed, documented password purely for local
grading convenience, not a pattern to extend to real provisioning). The new user changes it
themselves via `PUT /api/v1/auth/me/password` once they've logged in with the generated one.

## Disable, never delete

**Decision:** `POST /api/v1/users/{id}/disable` / `/enable` toggle a boolean `enabled` column
(`V7__add_user_enabled_flag.sql`). There is no `DELETE /api/v1/users/{id}` endpoint.

**Why:** `feature_flags.created_by`/`updated_by` and `audit_logs.actor_id` all reference `users.id`
with no `ON DELETE CASCADE` (by design — see the audit-immutability discussion in
`docs/production-readiness.md`). Hard-deleting a user with any flag or audit history would either
violate that foreign key outright, or require a cascade that would silently corrupt the audit
trail's "who did this" record — the exact thing the append-only audit design elsewhere in this
project (`AppendOnlyRepository`) exists to prevent. Disabling is also the primitive Spring
Security's `UserDetails#isEnabled()` is built around, so wiring it up gets login rejection and
(with one additional fix, below) per-request rejection essentially for free.

## Disabling takes effect immediately, not just at the next login

**A real bug found while implementing this, not a hypothetical:** `SecurityUser.isEnabled()`
delegates to `User.isEnabled()`, and `DaoAuthenticationProvider` (used by the login path) checks
it automatically — so a disabled account is correctly rejected at login. But
`JwtAuthenticationFilter`, which authenticates every *subsequent* request from an already-issued
token, does not go through `DaoAuthenticationProvider` at all — it builds the
`Authentication` object directly from a `UserDetailsService` lookup, bypassing the provider's own
pre-authentication checks entirely. Without an explicit fix, a JWT issued before an account was
disabled would keep working right up until it naturally expired (up to `JWT_EXPIRATION_MINUTES`,
default 60), which defeats the entire point of a "disable this account now" action.

**Fix:** `JwtAuthenticationFilter.authenticate()` now checks `userDetails.isEnabled()` itself
before granting the request access, using a **fresh database lookup on every request** (not a
claim baked into the token at issuance). This means disabling a user takes effect on that user's
very next API call, not just at their next login — verified directly in
`UserApiTest.aDisabledUserCanNoLongerLogInOrUseAnExistingToken`, which issues a token, disables
the account, and asserts the *same* previously-valid token is rejected on the very next request.

## An admin cannot disable their own account

**Decision:** `UserManagementService.disable` compares the target user ID against the acting
admin's own ID and rejects with a `400` if they match — enforced server-side (not just a hidden
button), and covered by both an integration test and an API test.

**Why:** without this guard, an admin working alone (a real possibility in a demo/single-admin
deployment) could disable their own account and have no way back in — there'd be no other admin
account to undo it. This is a narrow, cheap guard against a genuinely unrecoverable self-lockout,
not a general "can't modify your own account" restriction (an admin can still change their own
password normally).

## What was rejected

- **Email-based password reset.** Would need an actual email-sending integration and a
  time-limited reset-token flow — real infrastructure this demo-scoped feature doesn't need. The
  generated-password-at-creation plus self-service change-password combination covers the actual
  requirement (get a new person into the system, let them take over their own credentials from
  there) without it.
- **A `DELETE` endpoint that just lets the resulting foreign-key violation surface as a raw
  `409`.** Technically possible (`GlobalExceptionHandler` already maps
  `DataIntegrityViolationException` to a `409`), but exposing an endpoint whose failure mode is "it
  almost always throws once the account has done anything" is confusing API design, not a
  considered feature — disable already covers the real intent (revoke access) without the
  confusing failure mode.
