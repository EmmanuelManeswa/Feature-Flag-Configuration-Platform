package com.featureflagplatform.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RuleAssistantRequest(
        @NotBlank
        @Size(max = 500, message = "keep it to one sentence — a short, specific request produces a better proposal")
        String naturalLanguageRequest
) {
}
