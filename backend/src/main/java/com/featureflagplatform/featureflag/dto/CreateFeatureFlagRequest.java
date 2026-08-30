package com.featureflagplatform.featureflag.dto;

import com.featureflagplatform.evaluation.domain.FlagType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateFeatureFlagRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lower-kebab-case (e.g. new-dashboard)")
        String key,

        @NotBlank @Size(max = 255) String name,

        @Size(max = 1000) String description,

        @NotNull UUID environmentId,

        @NotNull FlagType type,

        boolean enabled,

        @Min(0) @Max(100) Integer rolloutPercentage,

        @Valid List<TargetingRuleDto> targetingRules
) {
}
