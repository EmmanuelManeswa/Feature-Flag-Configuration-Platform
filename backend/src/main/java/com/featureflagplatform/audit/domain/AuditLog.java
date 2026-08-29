package com.featureflagplatform.audit.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.featureflagplatform.auth.domain.User;
import com.featureflagplatform.environment.domain.Environment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An append-only audit row. There is deliberately no setter beyond
 * construction and no repository method that updates or deletes a row
 * (see {@link com.featureflagplatform.audit.repository.AuditLogRepository})
 * — immutability is enforced by simply never providing the means to mutate
 * one, not by a database trigger, which would be more robust for a real
 * production system but is more machinery than this project needs to make
 * the point (see docs/production-readiness.md).
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false, updatable = false)
    private User actor;

    @Column(name = "actor_email", nullable = false, updatable = false)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 50, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", updatable = false)
    private Environment environment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_value", columnDefinition = "jsonb", updatable = false)
    private JsonNode previousValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb", updatable = false)
    private JsonNode newValue;

    @Column(updatable = false)
    private Long version;

    @Column(name = "correlation_id", nullable = false, length = 100, updatable = false)
    private String correlationId;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 500, updatable = false)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(User actor, AuditAction action, String entityType, UUID entityId, Environment environment,
                     JsonNode previousValue, JsonNode newValue, Long version, String correlationId,
                     String ipAddress, String userAgent) {
        this.actor = Objects.requireNonNull(actor);
        this.actorEmail = actor.getEmail();
        this.action = Objects.requireNonNull(action);
        this.entityType = Objects.requireNonNull(entityType);
        this.entityId = Objects.requireNonNull(entityId);
        this.environment = environment;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.version = version;
        this.correlationId = Objects.requireNonNull(correlationId);
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public UUID getId() {
        return id;
    }

    public User getActor() {
        return actor;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public JsonNode getPreviousValue() {
        return previousValue;
    }

    public JsonNode getNewValue() {
        return newValue;
    }

    public Long getVersion() {
        return version;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
