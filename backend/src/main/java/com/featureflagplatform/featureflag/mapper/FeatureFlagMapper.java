package com.featureflagplatform.featureflag.mapper;

import com.featureflagplatform.featureflag.domain.FeatureFlag;
import com.featureflagplatform.featureflag.dto.FeatureFlagDto;
import com.featureflagplatform.featureflag.dto.TargetingRuleDto;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagMapper {

    public FeatureFlagDto toDto(FeatureFlag flag) {
        return new FeatureFlagDto(
                flag.getId(),
                flag.getKey(),
                flag.getName(),
                flag.getDescription(),
                flag.getEnvironment().getId(),
                flag.getEnvironment().getName(),
                flag.getType(),
                flag.isEnabled(),
                flag.getRolloutPercentage(),
                flag.getTargetingRules().stream().map(TargetingRuleDto::from).toList(),
                flag.getVersion(),
                flag.getCreatedBy().getEmail(),
                flag.getUpdatedBy().getEmail(),
                flag.getCreatedAt(),
                flag.getUpdatedAt());
    }
}
