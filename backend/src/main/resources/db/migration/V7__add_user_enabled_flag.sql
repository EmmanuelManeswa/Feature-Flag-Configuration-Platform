-- Supports disabling a user account. Deliberately not a DELETE capability:
-- users are referenced by feature_flags.created_by/updated_by and
-- audit_logs.actor_id with no ON DELETE CASCADE, so hard-deleting a user
-- with any history would violate those foreign keys — and audit rows must
-- always resolve to a real actor, permanently, not a tombstoned one.
-- Disabling is also what Spring Security's UserDetails#isEnabled() is
-- built around, so a disabled account is rejected at login (and on its
-- very next authenticated request, since SecurityUser is loaded fresh
-- from the database per request — see JwtAuthenticationFilter).

ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT true;
