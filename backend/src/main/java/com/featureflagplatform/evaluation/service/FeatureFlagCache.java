package com.featureflagplatform.evaluation.service;

import com.featureflagplatform.evaluation.domain.FeatureFlagSnapshot;

import java.util.Optional;
import java.util.UUID;

/**
 * The evaluation path (and the rest of the application) depends on this
 * interface, never on {@code RedisTemplate} directly — see
 * .claude/decisions/ADR-002-caching-strategy.md. An implementation is free
 * to fail closed (return {@code Optional.empty()} / no-op on write) rather
 * than throw: every caller treats a cache miss and a cache failure
 * identically, falling back to Postgres either way.
 */
public interface FeatureFlagCache {

    Optional<FeatureFlagSnapshot> get(UUID flagId);

    void put(FeatureFlagSnapshot snapshot);

    void evict(UUID flagId);
}
