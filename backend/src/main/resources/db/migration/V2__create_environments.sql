-- Environments (e.g. DEV / STAGING / PROD) that scope feature flags.
-- Deliberately just a name + description: environments are a low-cardinality
-- grouping concept here, not a full deployment-target model.

CREATE TABLE environments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(50)  NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_environments_name UNIQUE (name)
);
