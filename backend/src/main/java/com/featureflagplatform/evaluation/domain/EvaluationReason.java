package com.featureflagplatform.evaluation.domain;

/** Why an evaluation resolved the way it did — surfaced to the playground UI verbatim. */
public enum EvaluationReason {
    /** Flag's global `enabled` switch is off; nothing else was even considered. */
    FLAG_DISABLED,
    /** At least one targeting rule did not match the supplied context. */
    TARGETING_RULE_NOT_MATCHED,
    /** BOOLEAN flag, enabled, and (if present) all targeting rules matched. */
    BOOLEAN_MATCH,
    /** PERCENTAGE_ROLLOUT flag, eligible, and the deterministic bucket fell inside the rollout. */
    ROLLOUT_INCLUDED,
    /** PERCENTAGE_ROLLOUT flag, eligible, but the deterministic bucket fell outside the rollout. */
    ROLLOUT_EXCLUDED
}
