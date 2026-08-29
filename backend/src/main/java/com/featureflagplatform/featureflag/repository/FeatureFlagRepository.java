package com.featureflagplatform.featureflag.repository;

import com.featureflagplatform.featureflag.domain.FeatureFlag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByEnvironmentIdAndKey(UUID environmentId, String key);

    boolean existsByEnvironmentIdAndKey(UUID environmentId, String key);

    Page<FeatureFlag> findByEnvironmentId(UUID environmentId, Pageable pageable);
}
