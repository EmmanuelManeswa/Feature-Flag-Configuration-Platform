package com.featureflagplatform.audit.repository;

import com.featureflagplatform.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Deliberately exposes only what {@link JpaRepository} gives for reading and
 * {@code save} for inserting new rows — no custom update/delete query
 * methods are declared here, so there is no code path in the application
 * that can mutate or remove an existing audit row. (A hostile raw SQL
 * statement could still do it, same as with any ORM; a DB-level trigger
 * would close that gap for a real production deployment — see
 * docs/production-readiness.md.)
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByEntityIdOrderByCreatedAtDesc(UUID entityId, Pageable pageable);

    Page<AuditLog> findByEnvironmentIdOrderByCreatedAtDesc(UUID environmentId, Pageable pageable);

    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
