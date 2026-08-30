package com.featureflagplatform.environment.dto;

import com.featureflagplatform.environment.domain.Environment;

import java.time.Instant;
import java.util.UUID;

public record EnvironmentDto(UUID id, String name, String description, Instant createdAt, Instant updatedAt) {

    public static EnvironmentDto from(Environment environment) {
        return new EnvironmentDto(
                environment.getId(), environment.getName(), environment.getDescription(),
                environment.getCreatedAt(), environment.getUpdatedAt());
    }
}
