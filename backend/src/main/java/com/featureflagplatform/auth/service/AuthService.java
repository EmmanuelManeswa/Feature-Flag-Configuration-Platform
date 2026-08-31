package com.featureflagplatform.auth.service;

import com.featureflagplatform.auth.domain.User;
import com.featureflagplatform.auth.dto.ChangePasswordRequest;
import com.featureflagplatform.auth.dto.LoginRequest;
import com.featureflagplatform.auth.dto.LoginResponse;
import com.featureflagplatform.auth.dto.UserSummary;
import com.featureflagplatform.auth.repository.UserRepository;
import com.featureflagplatform.auth.security.JwtService;
import com.featureflagplatform.auth.security.SecurityUser;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager, JwtService jwtService,
            UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest request) {
        // AuthenticationManager throws BadCredentialsException on any
        // mismatch — deliberately the same exception/message whether the
        // email doesn't exist or the password is wrong, so a login attempt
        // never reveals which one was the problem.
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        var securityUser = (SecurityUser) authentication.getPrincipal();
        var user = securityUser.user();
        String token = jwtService.issueToken(user);

        return new LoginResponse(token, "Bearer", jwtService.expirationSeconds(), UserSummary.from(user));
    }

    /**
     * Self-service password change for the currently authenticated user —
     * requires the current password, so a hijacked-but-not-yet-logged-out
     * session can't be used to silently lock the real owner out permanently.
     */
    @Transactional
    public void changePassword(User user, ChangePasswordRequest request) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
