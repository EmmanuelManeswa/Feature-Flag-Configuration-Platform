package com.featureflagplatform.featureflag.service;

import com.featureflagplatform.TestcontainersConfiguration;
import com.featureflagplatform.audit.service.AuditService;
import com.featureflagplatform.auth.domain.User;
import com.featureflagplatform.auth.repository.UserRepository;
import com.featureflagplatform.common.exception.StaleVersionConflictException;
import com.featureflagplatform.environment.domain.Environment;
import com.featureflagplatform.environment.repository.EnvironmentRepository;
import com.featureflagplatform.evaluation.domain.FlagType;
import com.featureflagplatform.featureflag.dto.CreateFeatureFlagRequest;
import com.featureflagplatform.featureflag.dto.FeatureFlagDto;
import com.featureflagplatform.featureflag.dto.UpdateFeatureFlagRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises FeatureFlagService against a real Postgres (Testcontainers) —
 * deliberately not mocking the repository layer, because the bug this test
 * guards against (see the {@code saveAndFlush} comment in
 * {@code FeatureFlagService.update}) only shows up against a real Hibernate
 * flush/commit cycle. A mocked repository would have returned whatever
 * version the test told it to and hidden the bug entirely — this is caught
 * by manual smoke-testing first, then written up as a permanent regression
 * test here.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FeatureFlagServiceIntegrationTest {

    @Autowired
    private FeatureFlagService featureFlagService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private EnvironmentRepository environmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void updateIncrementsVersionAndTheResponseReflectsItImmediately() {
        Environment environment = environmentRepository.findByName("DEV").orElseThrow();
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        String key = "it-test-" + UUID.randomUUID();

        FeatureFlagDto created = featureFlagService.create(
                new CreateFeatureFlagRequest(key, "IT Test Flag", "desc", environment.getId(),
                        FlagType.BOOLEAN, true, null, List.of()),
                admin);
        assertThat(created.version()).isEqualTo(0L);

        FeatureFlagDto updated = featureFlagService.update(
                created.id(),
                new UpdateFeatureFlagRequest("IT Test Flag (renamed)", "desc", false, null, List.of(), 0L),
                admin);

        // The regression this guards: before the saveAndFlush fix, this came
        // back as 0 (the pre-update value) instead of 1.
        assertThat(updated.version()).isEqualTo(1L);
        assertThat(updated.name()).isEqualTo("IT Test Flag (renamed)");
        assertThat(updated.enabled()).isFalse();
    }

    @Test
    void updateWithStaleVersionIsRejectedWithBothVersionNumbers() {
        Environment environment = environmentRepository.findByName("DEV").orElseThrow();
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        String key = "it-test-" + UUID.randomUUID();

        FeatureFlagDto created = featureFlagService.create(
                new CreateFeatureFlagRequest(key, "Stale Test Flag", null, environment.getId(),
                        FlagType.BOOLEAN, true, null, List.of()),
                admin);

        // First edit makes an actual change (enabled true -> false) so Hibernate's
        // dirty-checking issues a real UPDATE and bumps the version to 1. An edit
        // that resubmits identical values is a legitimate no-op that Hibernate
        // correctly skips — worth knowing, since it's exactly the kind of case
        // that would make this test flaky if the "edit" didn't actually change
        // anything.
        featureFlagService.update(created.id(),
                new UpdateFeatureFlagRequest("Stale Test Flag", null, false, null, List.of(), 0L), admin);

        // Second edit still thinks the version is 0 (as if two people loaded
        // the same starting state and only one of them saved).
        assertThatThrownBy(() -> featureFlagService.update(created.id(),
                new UpdateFeatureFlagRequest("Conflicting edit", null, true, null, List.of(), 0L), admin))
                .isInstanceOf(StaleVersionConflictException.class)
                .satisfies(ex -> {
                    var conflict = (StaleVersionConflictException) ex;
                    assertThat(conflict.expectedVersion()).isEqualTo(0L);
                    assertThat(conflict.currentVersion()).isEqualTo(1L);
                });
    }

    @Test
    void createAndUpdateEachRecordAnImmutableAuditEntryWithBeforeAndAfterValues() {
        Environment environment = environmentRepository.findByName("DEV").orElseThrow();
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        String key = "it-test-" + UUID.randomUUID();

        FeatureFlagDto created = featureFlagService.create(
                new CreateFeatureFlagRequest(key, "Audit Test Flag", null, environment.getId(),
                        FlagType.PERCENTAGE_ROLLOUT, true, 10, List.of()),
                admin);

        featureFlagService.update(created.id(),
                new UpdateFeatureFlagRequest("Audit Test Flag", null, true, 40, List.of(), 0L), admin);

        var page = auditService.listByEntity(created.id(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);

        var entries = page.getContent();
        assertThat(entries).extracting("action").containsExactlyInAnyOrder("CREATE", "UPDATE");

        var updateEntry = entries.stream().filter(e -> e.action().equals("UPDATE")).findFirst().orElseThrow();
        assertThat(updateEntry.previousValue().get("rolloutPercentage").asInt()).isEqualTo(10);
        assertThat(updateEntry.newValue().get("rolloutPercentage").asInt()).isEqualTo(40);
        assertThat(updateEntry.actorEmail()).isEqualTo("admin@example.com");
    }
}
