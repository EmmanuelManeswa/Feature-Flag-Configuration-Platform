package com.featureflagplatform.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.featureflagplatform.audit.domain.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        String actorEmail,
        String action,
        String entityType,
        UUID entityId,
        UUID environmentId,
        JsonNode previousValue,
        JsonNode newValue,
        Long version,
        String correlationId,
        Instant createdAt
) {

    public static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getActorEmail(),
                log.getAction().name(),
                log.getEntityType(),
                log.getEntityId(),
                log.getEnvironment() == null ? null : log.getEnvironment().getId(),
                log.getPreviousValue(),
                log.getNewValue(),
                log.getVersion(),
                log.getCorrelationId(),
                log.getCreatedAt());
    }
}
