package com.featureflagplatform.evaluation.dto;

import com.featureflagplatform.evaluation.domain.TargetingRule;

/**
 * Read-only display shape of a targeting rule for the evaluation response.
 * Deliberately not the same type as {@code featureflag.dto.TargetingRuleDto}
 * (which carries Bean Validation for create/update requests) — that would
 * make the {@code evaluation} and {@code featureflag} packages depend on
 * each other in both directions. This one only ever goes out, never in.
 */
public record TargetingRuleView(String attribute, String operator, String value) {

    public static TargetingRuleView from(TargetingRule rule) {
        return new TargetingRuleView(rule.attribute(), rule.operator().name(), rule.value());
    }
}
