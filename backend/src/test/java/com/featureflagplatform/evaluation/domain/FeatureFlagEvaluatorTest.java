package com.featureflagplatform.evaluation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagEvaluatorTest {

    private static final UUID FLAG_ID = UUID.randomUUID();

    private static FeatureFlagSnapshot booleanFlag(boolean enabled, List<TargetingRule> rules) {
        return new FeatureFlagSnapshot(FLAG_ID, "dark-mode", "PROD", FlagType.BOOLEAN, enabled, null, rules, 1L);
    }

    private static FeatureFlagSnapshot rolloutFlag(int percentage, List<TargetingRule> rules) {
        return new FeatureFlagSnapshot(
                FLAG_ID, "advanced-search", "DEV", FlagType.PERCENTAGE_ROLLOUT, true, percentage, rules, 1L);
    }

    // --- disabled flag: always false, regardless of type or rules ---

    @Test
    void disabledFlagReturnsFalseWithFlagDisabledReason() {
        var flag = booleanFlag(false, List.of());
        var result = FeatureFlagEvaluator.evaluate(flag, EvaluationContext.of("user-1"));

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.FLAG_DISABLED);
        assertThat(result.bucket()).isNull();
    }

    // --- BOOLEAN strategy ---

    @Test
    void enabledBooleanFlagWithNoRulesResolvesTrue() {
        var flag = booleanFlag(true, List.of());
        var result = FeatureFlagEvaluator.evaluate(flag, EvaluationContext.of("user-1"));

        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.BOOLEAN_MATCH);
    }

    @Test
    void enabledBooleanFlagWithMatchingRuleResolvesTrue() {
        var rule = new TargetingRule("department", TargetingOperator.EQUALS, "ENGINEERING");
        var flag = booleanFlag(true, List.of(rule));
        var context = new EvaluationContext("user-1", Map.of("department", "ENGINEERING"));

        var result = FeatureFlagEvaluator.evaluate(flag, context);

        assertThat(result.value()).isTrue();
        assertThat(result.reason()).isEqualTo(EvaluationReason.BOOLEAN_MATCH);
    }

    @Test
    void enabledBooleanFlagWithNonMatchingRuleResolvesFalseAndReportsTheRule() {
        var rule = new TargetingRule("department", TargetingOperator.EQUALS, "ENGINEERING");
        var flag = booleanFlag(true, List.of(rule));
        var context = new EvaluationContext("user-1", Map.of("department", "SALES"));

        var result = FeatureFlagEvaluator.evaluate(flag, context);

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_RULE_NOT_MATCHED);
        assertThat(result.unmatchedRule()).isEqualTo(rule);
    }

    @Test
    void notEqualsRuleMatchesWhenAttributeIsAbsent() {
        var rule = new TargetingRule("userType", TargetingOperator.NOT_EQUALS, "INTERNAL_STAFF");
        var flag = booleanFlag(true, List.of(rule));
        var context = EvaluationContext.of("user-1"); // no attributes supplied

        var result = FeatureFlagEvaluator.evaluate(flag, context);

        assertThat(result.value()).isTrue();
    }

    @Test
    void equalsRuleNeverMatchesWhenAttributeIsAbsent() {
        var rule = new TargetingRule("location", TargetingOperator.EQUALS, "Harare");
        var flag = booleanFlag(true, List.of(rule));
        var context = EvaluationContext.of("user-1"); // no attributes supplied

        var result = FeatureFlagEvaluator.evaluate(flag, context);

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_RULE_NOT_MATCHED);
    }

    @Test
    void allRulesMustMatchNotJustOne() {
        var locationRule = new TargetingRule("location", TargetingOperator.EQUALS, "Harare");
        var staffRule = new TargetingRule("userType", TargetingOperator.NOT_EQUALS, "INTERNAL_STAFF");
        var flag = booleanFlag(true, List.of(locationRule, staffRule));

        // Matches location but fails the staff exclusion.
        var internalStaffInHarare = new EvaluationContext(
                "user-1", Map.of("location", "Harare", "userType", "INTERNAL_STAFF"));

        var result = FeatureFlagEvaluator.evaluate(flag, internalStaffInHarare);

        assertThat(result.value()).isFalse();
        assertThat(result.unmatchedRule()).isEqualTo(staffRule);
    }

    // --- PERCENTAGE_ROLLOUT strategy ---

    @Test
    void zeroPercentRolloutExcludesEveryone() {
        var flag = rolloutFlag(0, List.of());
        for (int i = 0; i < 200; i++) {
            var result = FeatureFlagEvaluator.evaluate(flag, EvaluationContext.of("user-" + i));
            assertThat(result.value()).as("user-%d should be excluded at 0%%", i).isFalse();
            assertThat(result.reason()).isEqualTo(EvaluationReason.ROLLOUT_EXCLUDED);
        }
    }

    @Test
    void hundredPercentRolloutIncludesEveryone() {
        var flag = rolloutFlag(100, List.of());
        for (int i = 0; i < 200; i++) {
            var result = FeatureFlagEvaluator.evaluate(flag, EvaluationContext.of("user-" + i));
            assertThat(result.value()).as("user-%d should be included at 100%%", i).isTrue();
            assertThat(result.reason()).isEqualTo(EvaluationReason.ROLLOUT_INCLUDED);
        }
    }

    @Test
    void sameUserGetsSameResultAcrossRepeatedEvaluations() {
        var flag = rolloutFlag(50, List.of());
        var context = EvaluationContext.of("consistent-user");

        var first = FeatureFlagEvaluator.evaluate(flag, context);
        for (int i = 0; i < 50; i++) {
            var repeat = FeatureFlagEvaluator.evaluate(flag, context);
            assertThat(repeat.value()).isEqualTo(first.value());
            assertThat(repeat.bucket()).isEqualTo(first.bucket());
        }
    }

    @Test
    void rolloutPercentageDistributionIsApproximatelyUniformAcrossManyUsers() {
        var flag = rolloutFlag(30, List.of());
        int sampleSize = 5000;
        long includedCount = 0;
        for (int i = 0; i < sampleSize; i++) {
            var result = FeatureFlagEvaluator.evaluate(flag, EvaluationContext.of("synthetic-user-" + i));
            if (result.value()) {
                includedCount++;
            }
        }
        double observedPercentage = (includedCount * 100.0) / sampleSize;
        // Generous tolerance (target 30%, allow 27-33%) to keep this non-flaky
        // while still catching a badly broken/biased hash distribution.
        assertThat(observedPercentage).isBetween(27.0, 33.0);
    }

    @Test
    void targetingRuleExclusionTakesPrecedenceOverBucketMembership() {
        var rule = new TargetingRule("location", TargetingOperator.EQUALS, "Harare");
        var flag = rolloutFlag(100, List.of(rule)); // 100% rollout, but gated by a rule
        var context = EvaluationContext.of("user-1"); // missing "location" attribute

        var result = FeatureFlagEvaluator.evaluate(flag, context);

        assertThat(result.value()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.TARGETING_RULE_NOT_MATCHED);
        assertThat(result.bucket()).isNull(); // never got as far as bucketing
    }

    // --- computeBucket: golden values cross-checked against an independent
    // Python (hashlib.sha256) implementation of the same documented algorithm,
    // so this pins the exact bytes-to-bucket mapping, not just internal
    // self-consistency. ---

    @ParameterizedTest
    @CsvSource({
            "ai-assistant, PROD, user-123, 15",
            "ai-assistant, PROD, user-456, 16",
            "advanced-search, DEV, alice@example.com, 56",
            "advanced-search, DEV, bob@example.com, 7"
    })
    void computeBucketMatchesIndependentReferenceImplementation(
            String flagKey, String environment, String stableId, int expectedBucket) {
        assertThat(FeatureFlagEvaluator.computeBucket(flagKey, environment, stableId))
                .isEqualTo(expectedBucket);
    }

    @Test
    void computeBucketIsAlwaysWithinValidRange() {
        for (int i = 0; i < 1000; i++) {
            int bucket = FeatureFlagEvaluator.computeBucket("some-flag", "PROD", "user-" + i);
            assertThat(bucket).isBetween(0, 99);
        }
    }

    @Test
    void differentFlagKeysProduceIndependentBucketsForTheSameUser() {
        // Same user, same environment, different flag key: buckets should not
        // be trivially correlated (e.g. both always identical or always off-by-one).
        int bucketA = FeatureFlagEvaluator.computeBucket("flag-a", "PROD", "same-user");
        int bucketB = FeatureFlagEvaluator.computeBucket("flag-b", "PROD", "same-user");
        assertThat(bucketA).isNotEqualTo(bucketB);
    }
}
