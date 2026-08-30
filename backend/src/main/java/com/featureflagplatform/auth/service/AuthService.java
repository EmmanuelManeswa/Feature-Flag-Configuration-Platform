package com.featureflagplatform.auth.service;

import com.featureflagplatform.auth.dto.LoginRequest;
import com.featureflagplatform.auth.dto.LoginResponse;
import com.featureflagplatform.auth.dto.UserSummary;
import com.featureflagplatform.auth.security.JwtService;
import com.featureflagplatform.auth.security.SecurityUser;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
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
}
