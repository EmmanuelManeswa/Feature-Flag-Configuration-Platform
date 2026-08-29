package com.featureflagplatform.evaluation.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * The evaluation engine. Deliberately a stateless, dependency-free class: no
 * Spring, no HTTP, no persistence, no I/O of any kind. Every unit test for
 * this class runs in milliseconds with no test infrastructure, which is the
 * entire point — this is the highest-risk business logic in the platform
 * (get it wrong and every flag lies to every caller), so it needs to be the
 * easiest code in the project to test exhaustively.
 *
 * <p>See {@code .claude/decisions/ADR-001-evaluation-algorithm.md} for the
 * rationale behind the bucketing algorithm.
 */
public final class FeatureFlagEvaluator {

    private FeatureFlagEvaluator() {
    }

    public static EvaluationResult evaluate(FeatureFlagSnapshot flag, EvaluationContext context) {
        if (!flag.enabled()) {
            return new EvaluationResult(
                    false, EvaluationReason.FLAG_DISABLED, null, null, flag.key(), flag.environmentName());
        }

        TargetingRule unmatchedRule = firstUnmatchedRule(flag.targetingRules(), context);
        if (unmatchedRule != null) {
            return new EvaluationResult(
                    false, EvaluationReason.TARGETING_RULE_NOT_MATCHED, null, unmatchedRule,
                    flag.key(), flag.environmentName());
        }

        if (flag.type() == FlagType.BOOLEAN) {
            return new EvaluationResult(
                    true, EvaluationReason.BOOLEAN_MATCH, null, null, flag.key(), flag.environmentName());
        }

        int bucket = computeBucket(flag.key(), flag.environmentName(), context.stableIdentifier());
        boolean included = bucket < flag.rolloutPercentage();
        EvaluationReason reason = included ? EvaluationReason.ROLLOUT_INCLUDED : EvaluationReason.ROLLOUT_EXCLUDED;
        return new EvaluationResult(included, reason, bucket, null, flag.key(), flag.environmentName());
    }

    private static TargetingRule firstUnmatchedRule(List<TargetingRule> rules, EvaluationContext context) {
        for (TargetingRule rule : rules) {
            if (!rule.matches(context)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Deterministic bucket in {@code [0, 100)} for {@code (flagKey, environmentName, stableIdentifier)}.
     *
     * <p><b>Algorithm:</b> SHA-256 the string {@code "{flagKey}:{environmentName}:{stableIdentifier}"},
     * take the first 4 bytes of the digest as an unsigned 32-bit big-endian integer, and reduce it
     * modulo 100.
     *
     * <p>This is deterministic — the same three inputs always hash to the same bucket, with no
     * server-side state to keep in sync across instances or restarts — and uniform enough for
     * rollout purposes, since SHA-256 digest bytes are effectively uniformly distributed. A caller's
     * bucket can only change if the flag's key, the environment, or their own stable identifier
     * changes; it never changes because of when they happened to ask. This is why {@code random()}
     * is explicitly disallowed: it would let the same user flip in and out of a rollout on every
     * request, which is unusable for anything that depends on a consistent experience (UI variants,
     * gradual migrations, etc).
     */
    public static int computeBucket(String flagKey, String environmentName, String stableIdentifier) {
        String input = flagKey + ":" + environmentName + ":" + stableIdentifier;
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory algorithm on every standard JVM implementation.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
        long unsignedFirstFourBytes =
                ((digest[0] & 0xFFL) << 24)
                        | ((digest[1] & 0xFFL) << 16)
                        | ((digest[2] & 0xFFL) << 8)
                        | (digest[3] & 0xFFL);
        return (int) (unsignedFirstFourBytes % 100);
    }
}
