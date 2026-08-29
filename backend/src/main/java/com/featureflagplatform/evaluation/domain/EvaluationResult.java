package com.featureflagplatform.evaluation.domain;

/**
 * The full, explainable outcome of one evaluation — everything the playground
 * UI shows a reviewer, not just the boolean result.
 *
 * @param value               the resolved on/off result
 * @param reason              why it resolved that way
 * @param bucket              the caller's deterministic bucket (0-99), or {@code null}
 *                            for BOOLEAN flags where bucketing never happened
 * @param unmatchedRule       the first targeting rule that failed, or {@code null}
 *                            if every rule matched (or there were none)
 * @param flagKey             echoed back for display convenience
 * @param environmentName     echoed back for display convenience
 */
public record EvaluationResult(
        boolean value,
        EvaluationReason reason,
        Integer bucket,
        TargetingRule unmatchedRule,
        String flagKey,
        String environmentName
) {
}
