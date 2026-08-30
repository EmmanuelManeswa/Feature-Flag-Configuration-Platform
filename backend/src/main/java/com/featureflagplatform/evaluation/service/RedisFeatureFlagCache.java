package com.featureflagplatform.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflagplatform.evaluation.domain.FeatureFlagSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache key format: {@code feature-flag:{flagId}} — keyed by the flag's UUID,
 * not by {@code environment:key}. This is a deliberate departure from the
 * more commonly suggested {@code {environment}:{flagKey}} pattern: the
 * platform's evaluate endpoint is {@code POST /api/v1/flags/{id}/evaluate},
 * which supplies the flag's ID directly from the URL with no database lookup
 * needed to obtain it. Keying the cache on the ID means a cache hit skips
 * Postgres entirely; keying it on {@code environment:key} would require
 * resolving the ID to those values first — which itself takes a Postgres
 * round-trip, defeating the point of caching on this endpoint shape. (A
 * separate SDK-facing endpoint that accepted {@code environment}/{@code key}
 * directly as request parameters could use that key format without
 * conflicting — Redis keys are just strings — but that endpoint isn't in
 * scope here.)
 *
 * <p>Every operation is wrapped so a Redis failure degrades to "acts like a
 * cache miss" (reads) or "silently does nothing" (writes) rather than
 * failing the request — see .claude/decisions/ADR-002-caching-strategy.md.
 * Postgres, not Redis, is the source of truth and the thing that must stay up.
 */
@Component
public class RedisFeatureFlagCache implements FeatureFlagCache {

    private static final Logger log = LoggerFactory.getLogger(RedisFeatureFlagCache.class);
    private static final String KEY_PREFIX = "feature-flag:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisFeatureFlagCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.cache.flag-ttl-seconds}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<FeatureFlagSnapshot> get(UUID flagId) {
        try {
            String json = redisTemplate.opsForValue().get(key(flagId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, FeatureFlagSnapshot.class));
        } catch (Exception e) {
            log.warn("Redis cache read failed for flag {}, falling back to Postgres", flagId, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(FeatureFlagSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(key(snapshot.id()), json, ttl);
        } catch (Exception e) {
            log.warn("Redis cache write failed for flag {} — next evaluation will just miss and repopulate", snapshot.id(), e);
        }
    }

    @Override
    public void evict(UUID flagId) {
        try {
            redisTemplate.delete(key(flagId));
        } catch (Exception e) {
            log.warn("Redis cache evict failed for flag {} — a stale entry may serve reads until TTL expiry ({}s)",
                    flagId, ttl.toSeconds(), e);
        }
    }

    private static String key(UUID flagId) {
        return KEY_PREFIX + flagId;
    }
}
