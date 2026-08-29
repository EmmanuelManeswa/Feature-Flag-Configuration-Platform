package com.featureflagplatform.evaluation.domain;

/**
 * Supported rollout strategies. BOOLEAN is a simple on/off switch;
 * PERCENTAGE_ROLLOUT gates on a deterministic bucket derived from the caller's
 * stable identifier (see {@link FeatureFlagEvaluator}). Both types can carry
 * targeting rules, which act as an eligibility pre-filter before the type's
 * own logic decides the result for eligible callers.
 */
public enum FlagType {
    BOOLEAN,
    PERCENTAGE_ROLLOUT
}
