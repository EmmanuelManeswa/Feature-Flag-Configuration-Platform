package com.featureflagplatform.featureflag.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published by {@link com.featureflagplatform.featureflag.service.FeatureFlagService}
 * on every create/update/delete, and broadcast to SSE subscribers by
 * {@link FlagChangeNotifier} only after the owning transaction commits (see
 * that class's Javadoc) — a subscriber never learns about a change that
 * later rolled back.
 *
 * <p>Deliberately a separate, smaller shape from {@code FeatureFlagDto}: a
 * change notification only needs to tell a listener *what changed and
 * where* so it knows to refetch, not carry the full flag payload (which
 * would also mean re-checking authorization per subscriber before deciding
 * how much of it to send).
 */
public record FlagChangeEvent(
        UUID flagId,
        String flagKey,
        UUID environmentId,
        FlagChangeType type,
        Instant occurredAt) {
}
