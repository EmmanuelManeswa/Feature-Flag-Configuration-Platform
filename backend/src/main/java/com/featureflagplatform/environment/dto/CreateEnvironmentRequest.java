package com.featureflagplatform.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "must be upper-case letters, digits, and underscores only (e.g. DEV, STAGING, PROD)")
        String name,

        @Size(max = 500)
        String description
) {
}
