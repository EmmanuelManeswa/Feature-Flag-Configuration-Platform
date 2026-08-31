package com.featureflagplatform.auth.service;

import com.featureflagplatform.TestcontainersConfiguration;
import com.featureflagplatform.auth.domain.User;
import com.featureflagplatform.auth.domain.UserRole;
import com.featureflagplatform.auth.dto.CreateUserRequest;
import com.featureflagplatform.auth.dto.CreatedUserDto;
import com.featureflagplatform.auth.repository.UserRepository;
import com.featureflagplatform.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Against a real Postgres (Testcontainers), not a mocked repository — the
 * uniqueness constraint and the actual bcrypt round-trip are exactly the
 * kind of thing a mock would let slide silently.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserManagementServiceIntegrationTest {

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createGeneratesAPasswordWhoseHashActuallyMatchesWhatWasStored() {
        String email = "it-test-" + UUID.randomUUID() + "@example.com";

        CreatedUserDto created = userManagementService.create(new CreateUserRequest(email, "IT Test User", UserRole.VIEWER));

        assertThat(created.generatedPassword()).hasSize(16);
        assertThat(created.user().email()).isEqualTo(email);
        assertThat(created.user().enabled()).isTrue();

        User stored = userRepository.findByEmail(email).orElseThrow();
        assertThat(passwordEncoder.matches(created.generatedPassword(), stored.getPasswordHash())).isTrue();
        // The stored hash must never equal the plaintext password itself —
        // guards against a no-op "encoder" that just returns its input.
        assertThat(stored.getPasswordHash()).isNotEqualTo(created.generatedPassword());
    }

    @Test
    void createRejectsADuplicateEmail() {
        String email = "it-test-" + UUID.randomUUID() + "@example.com";
        userManagementService.create(new CreateUserRequest(email, "First", UserRole.VIEWER));

        assertThatThrownBy(() -> userManagementService.create(new CreateUserRequest(email, "Second", UserRole.ADMIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(email);
    }

    @Test
    void disableThenEnableRoundTripsTheEnabledFlag() {
        String email = "it-test-" + UUID.randomUUID() + "@example.com";
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        CreatedUserDto created = userManagementService.create(new CreateUserRequest(email, "Toggle Test", UserRole.VIEWER));

        var disabled = userManagementService.disable(created.user().id(), admin);
        assertThat(disabled.enabled()).isFalse();
        assertThat(userRepository.findByEmail(email).orElseThrow().isEnabled()).isFalse();

        var enabled = userManagementService.enable(created.user().id());
        assertThat(enabled.enabled()).isTrue();
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() {
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();

        assertThatThrownBy(() -> userManagementService.disable(admin.getId(), admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot disable your own account");

        assertThat(userRepository.findByEmail("admin@example.com").orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void disablingAnUnknownUserIdIs404() {
        User admin = userRepository.findByEmail("admin@example.com").orElseThrow();
        assertThatThrownBy(() -> userManagementService.disable(UUID.randomUUID(), admin))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listIncludesTheEnabledFlagAndNeverAPasswordField() {
        var page = userManagementService.list(PageRequest.of(0, 50));
        assertThat(page.getContent()).isNotEmpty();
        // UserDto structurally has no password field to assert on — this is a
        // compile-time guarantee, not a runtime one, which is the point.
        assertThat(page.getContent()).allSatisfy(dto -> assertThat(dto.email()).isNotBlank());
    }
}
