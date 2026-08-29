-- Demo data for local development and the assessment review.
-- Both demo accounts use the password "Password123!" (documented in README.md).
-- Bcrypt hash generated with: htpasswd -bnBC 10 "" 'Password123!'
-- (Spring Security's BCryptPasswordEncoder accepts the $2y$ prefix htpasswd emits.)

DO $$
DECLARE
    admin_id       UUID;
    viewer_id      UUID;
    dev_env_id     UUID;
    staging_env_id UUID;
    prod_env_id    UUID;
    demo_hash      VARCHAR(255) := '$2y$10$LlHMpXVqw.E83WozuKfjLeostK3wJMzlMqmjO/B1xyfEGNEsA.3MG';
BEGIN
    INSERT INTO users (email, password_hash, display_name, role)
    VALUES ('admin@example.com', demo_hash, 'Demo Admin', 'ADMIN')
    RETURNING id INTO admin_id;

    INSERT INTO users (email, password_hash, display_name, role)
    VALUES ('viewer@example.com', demo_hash, 'Demo Viewer', 'VIEWER')
    RETURNING id INTO viewer_id;

    INSERT INTO environments (name, description)
    VALUES ('DEV', 'Development environment')
    RETURNING id INTO dev_env_id;

    INSERT INTO environments (name, description)
    VALUES ('STAGING', 'Pre-production staging environment')
    RETURNING id INTO staging_env_id;

    INSERT INTO environments (name, description)
    VALUES ('PROD', 'Production environment')
    RETURNING id INTO prod_env_id;

    -- Simple boolean flag, on in DEV, off in PROD — shows environment isolation.
    INSERT INTO feature_flags (key, name, description, environment_id, type, enabled, created_by, updated_by)
    VALUES ('new-dashboard', 'New Dashboard', 'Redesigned analytics dashboard', dev_env_id, 'BOOLEAN', true, admin_id, admin_id);

    INSERT INTO feature_flags (key, name, description, environment_id, type, enabled, created_by, updated_by)
    VALUES ('new-dashboard', 'New Dashboard', 'Redesigned analytics dashboard', prod_env_id, 'BOOLEAN', false, admin_id, admin_id);

    -- Enabled everywhere — good "boring, obviously-on" control case for the playground.
    INSERT INTO feature_flags (key, name, description, environment_id, type, enabled, created_by, updated_by)
    VALUES ('dark-mode', 'Dark Mode', 'System-wide dark theme', dev_env_id, 'BOOLEAN', true, admin_id, admin_id);

    INSERT INTO feature_flags (key, name, description, environment_id, type, enabled, created_by, updated_by)
    VALUES ('dark-mode', 'Dark Mode', 'System-wide dark theme', prod_env_id, 'BOOLEAN', true, admin_id, admin_id);

    -- Plain percentage rollout, no targeting — shows deterministic bucketing on its own.
    INSERT INTO feature_flags (key, name, description, environment_id, type, enabled, rollout_percentage, created_by, updated_by)
    VALUES ('advanced-search', 'Advanced Search', 'Faceted search with filters', dev_env_id, 'PERCENTAGE_ROLLOUT', true, 50, admin_id, admin_id);

    -- Percentage rollout + targeting rules — mirrors the assessment's own example
    -- ("enable this for 20% of users in Harare except internal staff").
    INSERT INTO feature_flags (
        key, name, description, environment_id, type, enabled, rollout_percentage, targeting_rules, created_by, updated_by
    )
    VALUES (
        'ai-assistant', 'AI Assistant', 'In-app AI rule assistant', prod_env_id, 'PERCENTAGE_ROLLOUT', true, 20,
        '[
            {"attribute": "location", "operator": "EQUALS", "value": "Harare"},
            {"attribute": "userType", "operator": "NOT_EQUALS", "value": "INTERNAL_STAFF"}
        ]'::jsonb,
        admin_id, admin_id
    );

    -- Fully disabled flag — a realistic "not ready yet" state.
    INSERT INTO feature_flags (key, name, description, environment_id, type, enabled, created_by, updated_by)
    VALUES ('payments-v2', 'Payments V2', 'Next-generation payments pipeline', prod_env_id, 'BOOLEAN', false, admin_id, admin_id);

    -- Staging gets a lighter footprint, deliberately: shows an empty-ish environment in the UI.
    INSERT INTO feature_flags (key, name, description, environment_id, type, enabled, created_by, updated_by)
    VALUES ('dark-mode', 'Dark Mode', 'System-wide dark theme', staging_env_id, 'BOOLEAN', true, admin_id, admin_id);
END $$;
