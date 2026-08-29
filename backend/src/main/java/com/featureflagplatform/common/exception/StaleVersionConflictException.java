package com.featureflagplatform.common.exception;

import java.util.UUID;

/**
 * Thrown when a client submits an update against a version that no longer
 * matches the persisted entity's version — someone else changed it first.
 * Distinct from letting Hibernate's {@code ObjectOptimisticLockingFailureException}
 * surface directly: this is thrown proactively by the service layer, before
 * any write is attempted, which is both faster and lets the response carry the
 * actual current version so the frontend can show it without a second request.
 */
public class StaleVersionConflictException extends RuntimeException {

    private final UUID entityId;
    private final long expectedVersion;
    private final long currentVersion;

    public StaleVersionConflictException(UUID entityId, long expectedVersion, long currentVersion) {
        super("Feature flag %s was modified by another user (expected version %d, current version %d)"
                .formatted(entityId, expectedVersion, currentVersion));
        this.entityId = entityId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public UUID entityId() {
        return entityId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
