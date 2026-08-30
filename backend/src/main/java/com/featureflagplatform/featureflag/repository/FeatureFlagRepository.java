package com.featureflagplatform.featureflag.repository;

import com.featureflagplatform.featureflag.domain.FeatureFlag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    /**
     * Overridden to eagerly join {@code environment} in the same query. Without
     * this, {@code environment} stays a lazy proxy that only resolves within an
     * open Hibernate session — fine when called from a {@code @Transactional}
     * service method, but {@link com.featureflagplatform.evaluation.service.EvaluationService}
     * deliberately calls this outside of any transaction (see ADR-002: "no
     * transaction spans a cache call"), and with {@code open-in-view: false}
     * there's no session left by the time {@code FeatureFlag.toSnapshot()} calls
     * {@code environment.getName()} — that was a real
     * {@code LazyInitializationException} caught by manual smoke-testing, not a
     * hypothetical. The join also means the evaluation hot path is always one
     * query, never two.
     */
    @Override
    @EntityGraph(attributePaths = "environment")
    Optional<FeatureFlag> findById(UUID id);

    Optional<FeatureFlag> findByEnvironmentIdAndKey(UUID environmentId, String key);

    boolean existsByEnvironmentIdAndKey(UUID environmentId, String key);

    Page<FeatureFlag> findByEnvironmentId(UUID environmentId, Pageable pageable);
}
