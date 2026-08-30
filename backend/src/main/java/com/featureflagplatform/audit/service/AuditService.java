package com.featureflagplatform.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflagplatform.audit.domain.AuditAction;
import com.featureflagplatform.audit.domain.AuditLog;
import com.featureflagplatform.audit.dto.AuditLogDto;
import com.featureflagplatform.audit.repository.AuditLogRepository;
import com.featureflagplatform.auth.domain.User;
import com.featureflagplatform.common.observability.CorrelationId;
import com.featureflagplatform.environment.domain.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single write path for audit rows. Every configuration mutation in the
 * platform (currently: feature flag create/update) calls {@link #record}
 * inside the same transaction as the change it's recording — either both the
 * change and its audit row commit, or neither does. There is no "fire and
 * forget" audit write; a mutation whose audit record couldn't be written is
 * a mutation that didn't happen, as far as this platform is concerned.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(User actor, AuditAction action, String entityType, UUID entityId,
                        Environment environment, Object previousValue, Object newValue, Long version) {
        JsonNode previousNode = previousValue == null ? null : objectMapper.valueToTree(previousValue);
        JsonNode newNode = newValue == null ? null : objectMapper.valueToTree(newValue);

        AuditLog auditLog = new AuditLog(
                actor, action, entityType, entityId, environment,
                previousNode, newNode, version, CorrelationId.current(),
                null, null);
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> listAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(AuditLogDto::from);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> listByEntity(UUID entityId, Pageable pageable) {
        return auditLogRepository.findByEntityIdOrderByCreatedAtDesc(entityId, pageable).map(AuditLogDto::from);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogDto> listByEnvironment(UUID environmentId, Pageable pageable) {
        return auditLogRepository.findByEnvironmentIdOrderByCreatedAtDesc(environmentId, pageable).map(AuditLogDto::from);
    }
}
