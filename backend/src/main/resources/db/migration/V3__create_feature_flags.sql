-- Feature flags. `targeting_rules` is a JSONB array of
-- {"attribute": string, "operator": "EQUALS"|"NOT_EQUALS", "value": string}
-- validated by application code (Bean Validation + domain rules) before it ever
-- reaches this column — the DB constraint is a defense-in-depth backstop, not
-- the primary validation layer.
--
-- `version` backs optimistic-locking (JPA @Version): concurrent updates against
-- a stale version are rejected with 409 rather than silently overwritten.

CREATE TABLE feature_flags (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key                 VARCHAR(100)  NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    description         VARCHAR(1000),
    environment_id      UUID          NOT NULL REFERENCES environments(id),
    type                VARCHAR(30)   NOT NULL,
    enabled             BOOLEAN       NOT NULL DEFAULT false,
    rollout_percentage  INT,
    targeting_rules     JSONB         NOT NULL DEFAULT '[]'::jsonb,
    version             BIGINT        NOT NULL DEFAULT 0,
    created_by          UUID          NOT NULL REFERENCES users(id),
    updated_by          UUID          NOT NULL REFERENCES users(id),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_feature_flags_env_key UNIQUE (environment_id, key),
    CONSTRAINT chk_feature_flags_type CHECK (type IN ('BOOLEAN', 'PERCENTAGE_ROLLOUT')),
    CONSTRAINT chk_feature_flags_key_format CHECK (key ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT chk_feature_flags_rollout_range
        CHECK (rollout_percentage IS NULL OR rollout_percentage BETWEEN 0 AND 100),
    CONSTRAINT chk_feature_flags_rollout_matches_type CHECK (
        (type = 'PERCENTAGE_ROLLOUT' AND rollout_percentage IS NOT NULL) OR
        (type = 'BOOLEAN' AND rollout_percentage IS NULL)
    )
);
