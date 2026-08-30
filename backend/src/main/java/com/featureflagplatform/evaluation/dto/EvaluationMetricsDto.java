package com.featureflagplatform.evaluation.dto;

import java.util.Map;

/**
 * Basic evaluation metrics for one flag: how many times it's been
 * evaluated, broken down by result. Sourced from the same Micrometer
 * counters {@link com.featureflagplatform.evaluation.service.EvaluationService}
 * already increments on every evaluation — this DTO just scopes and shapes
 * them per flag instead of leaving them only in aggregate Prometheus text
 * format at /actuator/prometheus.
 *
 * @param flagKey the flag this summary is scoped to
 * @param countsByResult evaluation count per result value (e.g. {"true": 42, "false": 8}) —
 *                        empty if the flag has never been evaluated since the backend started
 * @param totalEvaluations sum of countsByResult, provided directly so a client doesn't have to
 */
public record EvaluationMetricsDto(String flagKey, Map<String, Long> countsByResult, long totalEvaluations) {
}
