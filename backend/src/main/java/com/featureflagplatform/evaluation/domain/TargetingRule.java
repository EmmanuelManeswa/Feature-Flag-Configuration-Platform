package com.featureflagplatform.evaluation.domain;

import java.util.Objects;

/**
 * A single targeting condition: does the evaluation context's {@code attribute}
 * satisfy {@code operator} against {@code value}? Attribute names are free-form
 * strings resolved against {@link EvaluationContext#attributes()} — new
 * attributes (department, location, plan tier, whatever comes next) need no
 * code change here, only a new key in the context map. This is what makes the
 * targeting model able to "evolve" per the assessment brief without touching
 * the evaluator.
 *
 * <p>Comparison is case-sensitive and exact-match by design: fuzzy/partial
 * matching would make rollout membership harder to reason about and to test.
 */
public record TargetingRule(String attribute, TargetingOperator operator, String value) {

    public TargetingRule {
        Objects.requireNonNull(attribute, "attribute must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (attribute.isBlank()) {
            throw new IllegalArgumentException("attribute must not be blank");
        }
    }

    /**
     * A rule matches when the context is missing the attribute entirely only
     * in the NOT_EQUALS case (absence is treated as "not equal to anything").
     * An EQUALS rule against a missing attribute never matches — you can't be
     * targeted by a value you don't have.
     */
    public boolean matches(EvaluationContext context) {
        String actual = context.attributes().get(attribute);
        return switch (operator) {
            case EQUALS -> actual != null && actual.equals(value);
            case NOT_EQUALS -> actual == null || !actual.equals(value);
        };
    }
}
