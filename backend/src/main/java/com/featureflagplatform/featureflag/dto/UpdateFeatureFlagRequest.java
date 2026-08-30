package com.featureflagplatform.featureflag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code key}, {@code environmentId}, and {@code type} are not editable —
 * they define the flag's identity. Changing a flag's strategy after the fact
 * would make its own audit history confusing to read ("was this always a
 * rollout, or did it used to be a boolean?"); the straightforward path for
 * that is deleting and recreating the flag, which is itself auditable.
 *
 * <p>{@code expectedVersion} is the optimistic-concurrency check: it must
 * match the flag's current version or the update is rejected with 409
 * (see {@link com.featureflagplatform.common.exception.StaleVersionConflictException}).
 */
public record UpdateFeatureFlagRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        boolean enabled,
        @Min(0) @Max(100) Integer rolloutPercentage,
        @Valid List<TargetingRuleDto> targetingRules,
        @NotNull Long expectedVersion
) {
}
