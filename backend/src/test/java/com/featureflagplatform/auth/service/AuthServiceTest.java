package com.featureflagplatform.auth.service;

import com.featureflagplatform.auth.domain.User;
import com.featureflagplatform.auth.domain.UserRole;
import com.featureflagplatform.auth.dto.ChangePasswordRequest;
import com.featureflagplatform.auth.dto.LoginRequest;
import com.featureflagplatform.auth.repository.UserRepository;
import com.featureflagplatform.auth.security.JwtService;
import com.featureflagplatform.auth.security.SecurityUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void successfulLoginReturnsTokenAndUserSummary() {
        var user = new User("admin@example.com", "hashed", "Demo Admin", UserRole.ADMIN);
        var securityUser = new SecurityUser(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.issueToken(user)).thenReturn("signed.jwt.token");
        when(jwtService.expirationSeconds()).thenReturn(3600L);

        var authService = new AuthService(authenticationManager, jwtService, userRepository, passwordEncoder);
        var response = authService.login(new LoginRequest("admin@example.com", "Password123!"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("admin@example.com");
        assertThat(response.user().role()).isEqualTo("ADMIN");
    }

    @Test
    void badCredentialsPropagatesAsIsForTheGlobalExceptionHandlerToMapTo401() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        var authService = new AuthService(authenticationManager, jwtService, userRepository, passwordEncoder);

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void changePasswordRejectsAnIncorrectCurrentPasswordWithoutTouchingTheStoredHash() {
        var user = new User("admin@example.com", "old-hash", "Demo Admin", UserRole.ADMIN);
        when(passwordEncoder.matches("wrong-current", "old-hash")).thenReturn(false);

        var authService = new AuthService(authenticationManager, jwtService, userRepository, passwordEncoder);

        assertThatThrownBy(() -> authService.changePassword(user, new ChangePasswordRequest("wrong-current", "NewPassword1!")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordHashesAndPersistsTheNewPasswordWhenTheCurrentOneMatches() {
        var user = new User("admin@example.com", "old-hash", "Demo Admin", UserRole.ADMIN);
        when(passwordEncoder.matches("correct-current", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("new-hash");

        var authService = new AuthService(authenticationManager, jwtService, userRepository, passwordEncoder);
        authService.changePassword(user, new ChangePasswordRequest("correct-current", "NewPassword1!"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }
}
