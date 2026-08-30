package com.featureflagplatform.featureflag.dto;

import com.featureflagplatform.evaluation.domain.TargetingOperator;
import com.featureflagplatform.evaluation.domain.TargetingRule;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The API-facing shape of a targeting rule. Kept separate from
 * {@link TargetingRule} (rather than putting Bean Validation annotations
 * directly on that record) specifically so {@code evaluation.domain} stays
 * free of any framework dependency, including {@code jakarta.validation} —
 * see .claude/CLAUDE.md's "evaluation engine is framework-free" rule.
 *
 * <p>This is also the exact shape the AI rule assistant's proposals are
 * validated against (see {@code ai.service.AiRuleAssistantService}) —
 * human-submitted and AI-proposed targeting rules go through identical
 * validation because they're literally the same type. The {@code attribute}
 * pattern is deliberately an open identifier shape rather than a hardcoded
 * enum of allowed names (targeting needs to stay extensible — see
 * {@code TargetingRule}'s Javadoc), but it does rule out anything that isn't
 * a plain identifier, which is what actually matters for an AI-sourced value:
 * no whitespace, no punctuation, no injection-shaped content.
 */
public record TargetingRuleDto(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "must be a plain identifier (letters, digits, underscore, starting with a letter)")
        String attribute,

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
