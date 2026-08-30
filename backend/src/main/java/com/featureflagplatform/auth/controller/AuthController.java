package com.featureflagplatform.auth.controller;

import com.featureflagplatform.auth.dto.LoginRequest;
import com.featureflagplatform.auth.dto.LoginResponse;
import com.featureflagplatform.auth.dto.UserSummary;
import com.featureflagplatform.auth.security.SecurityUser;
import com.featureflagplatform.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login and current-session identity")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements // overrides the global bearer requirement: logging in needs no token yet
    @Operation(
            summary = "Log in with email and password",
            description = "Returns a stateless JWT access token. Demo accounts: admin@example.com / "
                    + "viewer@example.com, both with password \"Password123!\".")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated successfully"),
            @ApiResponse(responseCode = "400", description = "Malformed request (missing/invalid email or password)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Email or password is incorrect",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<UserSummary> me(Authentication authentication) {
        var securityUser = (SecurityUser) authentication.getPrincipal();
        return ResponseEntity.ok(UserSummary.from(securityUser.user()));
    }
}
