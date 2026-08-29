package com.featureflagplatform.evaluation.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, framework-free view of a feature flag's configuration at the
 * instant of evaluation — no JPA, no Jackson, no HTTP. This is what both the
 * cache and the database map into before handing off to
 * {@link FeatureFlagEvaluator}, and it's what unit tests construct directly
 * without needing Spring or a database at all.
 */
public record FeatureFlagSnapshot(
        UUID id,
        String key,
        String environmentName,
        FlagType type,
        boolean enabled,
        Integer rolloutPercentage,
        List<TargetingRule> targetingRules,
        long version
) {

    public FeatureFlagSnapshot {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(environmentName, "environmentName must not be null");
        Objects.requireNonNull(type, "type must not be null");
        targetingRules = targetingRules == null ? List.of() : List.copyOf(targetingRules);

        if (type == FlagType.PERCENTAGE_ROLLOUT) {
            if (rolloutPercentage == null || rolloutPercentage < 0 || rolloutPercentage > 100) {
                throw new IllegalArgumentException(
                        "rolloutPercentage must be between 0 and 100 for PERCENTAGE_ROLLOUT flags");
            }
        } else if (rolloutPercentage != null) {
            throw new IllegalArgumentException("rolloutPercentage must be null for BOOLEAN flags");
        }
    }
}
