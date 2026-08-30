package com.featureflagplatform.evaluation.dto;

import com.featureflagplatform.evaluation.domain.EvaluationResult;

public record EvaluationResultDto(
        boolean value,
        String reason,
        Integer bucket,
        TargetingRuleView unmatchedRule,
        String flagKey,
        String environmentName,
        boolean cacheHit,
        long evaluationLatencyMicros
) {

    public static EvaluationResultDto from(EvaluationResult result, boolean cacheHit, long evaluationLatencyMicros) {
        return new EvaluationResultDto(
                result.value(),
                result.reason().name(),
                result.bucket(),
                result.unmatchedRule() == null ? null : TargetingRuleView.from(result.unmatchedRule()),
                result.flagKey(),
                result.environmentName(),
                cacheHit,
                evaluationLatencyMicros);
    }
}
