package com.featureflagplatform.featureflag.dto;

import com.featureflagplatform.evaluation.domain.FlagType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeatureFlagDto(
        UUID id,
        String key,
        String name,
        String description,
        UUID environmentId,
        String environmentName,
        FlagType type,
        boolean enabled,
        Integer rolloutPercentage,
        List<TargetingRuleDto> targetingRules,
        long version,
        String createdByEmail,
        String updatedByEmail,
        Instant createdAt,
        Instant updatedAt
) {
}
