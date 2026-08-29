-- Immutable audit trail. Rows are append-only: no application code ever issues
-- an UPDATE or DELETE against this table (enforced by convention + the fact
-- that AuditLogRepository only exposes save/find, never update/delete).
--
-- actor_email is a denormalized snapshot (not just actor_id) so the audit
-- record remains meaningful even if the user's email later changes.
-- previous_value/new_value store the flag's DTO shape as JSONB so a reviewer
-- can see exactly what changed without joining back to feature_flag_versions
-- that no longer exist (there's no separate row-per-version table; the audit
-- log itself is the version history).

CREATE TABLE audit_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id         UUID          NOT NULL REFERENCES users(id),
    actor_email      VARCHAR(255)  NOT NULL,
    action           VARCHAR(30)   NOT NULL,
    entity_type      VARCHAR(50)   NOT NULL,
    entity_id        UUID          NOT NULL,
    environment_id   UUID          REFERENCES environments(id),
    previous_value   JSONB,
    new_value        JSONB,
    version          BIGINT,
    correlation_id   VARCHAR(100)  NOT NULL,
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(500),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_audit_logs_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE'))
);
