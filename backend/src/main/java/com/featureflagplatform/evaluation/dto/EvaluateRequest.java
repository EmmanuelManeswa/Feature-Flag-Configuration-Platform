package com.featureflagplatform.evaluation.dto;

import com.featureflagplatform.evaluation.domain.EvaluationContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record EvaluateRequest(
        @NotBlank
        @Size(max = 255)
        String stableIdentifier,

        @Size(max = 20)
        Map<@Size(max = 100) String, @Size(max = 500) String> attributes
) {

    public EvaluationContext toDomain() {
        return new EvaluationContext(stableIdentifier, attributes);
    }
}
