package com.featureflagplatform.audit.repository;

import com.featureflagplatform.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Extends {@link AppendOnlyRepository}, not {@code JpaRepository} — there is
 * no delete method anywhere in this repository's type hierarchy, so no
 * application code can mutate or remove an existing audit row, now or in a
 * future change. (A hostile raw SQL statement could still do it, same as
 * with any ORM; a DB-level trigger would close that gap for a real
 * production deployment — see docs/production-readiness.md.)
 */
public interface AuditLogRepository extends AppendOnlyRepository<AuditLog, UUID> {

    Page<AuditLog> findByEntityIdOrderByCreatedAtDesc(UUID entityId, Pageable pageable);

    Page<AuditLog> findByEnvironmentIdOrderByCreatedAtDesc(UUID environmentId, Pageable pageable);

    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
