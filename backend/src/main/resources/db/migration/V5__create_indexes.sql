-- Indexes chosen for actual access patterns, not blanket coverage:
--
-- * feature_flags(environment_id, key) is already indexed by the UNIQUE
--   constraint in V3, and Postgres can use its leading column (environment_id)
--   for the "list flags in an environment" query — no extra index needed.
-- * audit_logs is append-only and queried almost exclusively by "history for
--   this entity" and "recent activity for this environment", both ordered by
--   recency, so DESC is baked into the index rather than left to a runtime sort.
-- * actor_id lookups ("what has this user changed") are a secondary but real
--   audit use case, so it gets the same treatment.

CREATE INDEX idx_audit_logs_entity_id_created_at
    ON audit_logs (entity_id, created_at DESC);

CREATE INDEX idx_audit_logs_actor_id_created_at
    ON audit_logs (actor_id, created_at DESC);

CREATE INDEX idx_audit_logs_environment_id_created_at
    ON audit_logs (environment_id, created_at DESC);
