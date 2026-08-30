package com.featureflagplatform.featureflag.dto;

import com.featureflagplatform.evaluation.domain.TargetingOperator;
import com.featureflagplatform.evaluation.domain.TargetingRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The API-facing shape of a targeting rule. Kept separate from
 * {@link TargetingRule} (rather than putting Bean Validation annotations
 * directly on that record) specifically so {@code evaluation.domain} stays
 * free of any framework dependency, including {@code jakarta.validation} —
 * see .claude/CLAUDE.md's "evaluation engine is framework-free" rule.
 */
public record TargetingRuleDto(
        @NotBlank @Size(max = 100) String attribute,
        @NotNull TargetingOperator operator,
        @NotBlank @Size(max = 500) String value
) {

    public TargetingRule toDomain() {
        return new TargetingRule(attribute, operator, value);
    }

    public static TargetingRuleDto from(TargetingRule rule) {
        return new TargetingRuleDto(rule.attribute(), rule.operator(), rule.value());
    }
}
