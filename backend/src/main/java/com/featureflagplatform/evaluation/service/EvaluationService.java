package com.featureflagplatform.evaluation.service;

import com.featureflagplatform.common.exception.ResourceNotFoundException;
import com.featureflagplatform.evaluation.domain.EvaluationContext;
import com.featureflagplatform.evaluation.domain.EvaluationResult;
import com.featureflagplatform.evaluation.domain.FeatureFlagEvaluator;
import com.featureflagplatform.evaluation.domain.FeatureFlagSnapshot;
import com.featureflagplatform.evaluation.dto.EvaluationResultDto;
import com.featureflagplatform.featureflag.repository.FeatureFlagRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The request path for a single evaluation:
 * Redis (cache-aside, keyed by flag ID) → on miss, Postgres → repopulate
 * cache → run the pure {@link FeatureFlagEvaluator}. See
 * .claude/decisions/ADR-002-caching-strategy.md for the consistency model
 * (bounded staleness up to the cache TTL between mutation and eviction is
 * theoretically possible but practically near-zero, since
 * {@code FeatureFlagService} writes the fresh snapshot to cache in the same
 * request that commits the mutation — see ADR-002 for the exact sequencing).
 *
 * <p>No transaction spans a cache call: {@code @Transactional(readOnly =
 * true)} covers only the Postgres fallback query, keeping Redis entirely
 * outside the database transaction boundary.
 */
@Service
public class EvaluationService {

    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureFlagCache cache;
    private final MeterRegistry meterRegistry;

    public EvaluationService(FeatureFlagRepository featureFlagRepository, FeatureFlagCache cache, MeterRegistry meterRegistry) {
        this.featureFlagRepository = featureFlagRepository;
        this.cache = cache;
        this.meterRegistry = meterRegistry;
    }

    public EvaluationResultDto evaluate(UUID flagId, EvaluationContext context) {
        long startNanos = System.nanoTime();

        var cached = cache.get(flagId);
        boolean cacheHit = cached.isPresent();
        meterRegistry.counter("feature_flag.cache.requests", "result", cacheHit ? "hit" : "miss").increment();

        FeatureFlagSnapshot snapshot = cached.orElseGet(() -> loadAndCache(flagId));

        EvaluationResult result = FeatureFlagEvaluator.evaluate(snapshot, context);

        meterRegistry.counter("feature_flag.evaluations", "flag", snapshot.key(), "result", String.valueOf(result.value()))
                .increment();

        long latencyMicros = (System.nanoTime() - startNanos) / 1_000;
        meterRegistry.timer("feature_flag.evaluation.latency").record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);

        return EvaluationResultDto.from(result, cacheHit, latencyMicros);
    }

    /**
     * No explicit {@code @Transactional} here: this is a single Spring Data
     * repository call, and {@code SimpleJpaRepository} already wraps each of
     * its own methods in a read-only transaction — an annotation on this
     * method would be a self-invocation no-op anyway (calling a method on
     * {@code this} bypasses Spring's transactional proxy entirely), so it's
     * better left off than left on and misleading.
     */
    private FeatureFlagSnapshot loadAndCache(UUID flagId) {
        FeatureFlagSnapshot snapshot = featureFlagRepository.findById(flagId)
                .orElseThrow(() -> new ResourceNotFoundException("FeatureFlag", flagId))
                .toSnapshot();
        cache.put(snapshot);
        return snapshot;
    }
}
