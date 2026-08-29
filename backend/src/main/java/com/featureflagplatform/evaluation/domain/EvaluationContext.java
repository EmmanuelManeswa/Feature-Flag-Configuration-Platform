package com.featureflagplatform.evaluation.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Everything the evaluator knows about the caller for a single evaluation.
 *
 * <p>{@code stableIdentifier} is what percentage-rollout bucketing hashes on —
 * it must stay the same across requests for the same real-world user (a user
 * ID or, failing that, an email) or their bucket membership will flap between
 * calls, which is exactly what the deterministic-hash approach exists to
 * prevent. It is required even for BOOLEAN flags for API consistency, but
 * only PERCENTAGE_ROLLOUT actually uses it.
 *
 * <p>{@code attributes} is an open map (email, department, location, plan,
 * whatever a targeting rule wants to reference) rather than fixed fields —
 * see {@link TargetingRule} for why.
 */
public record EvaluationContext(String stableIdentifier, Map<String, String> attributes) {

    public EvaluationContext {
        Objects.requireNonNull(stableIdentifier, "stableIdentifier must not be null");
        if (stableIdentifier.isBlank()) {
            throw new IllegalArgumentException("stableIdentifier must not be blank");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static EvaluationContext of(String stableIdentifier) {
        return new EvaluationContext(stableIdentifier, Map.of());
    }
}
