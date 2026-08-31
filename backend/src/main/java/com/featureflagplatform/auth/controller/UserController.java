package com.featureflagplatform.auth.controller;

import com.featureflagplatform.auth.dto.CreateUserRequest;
import com.featureflagplatform.auth.dto.CreatedUserDto;
import com.featureflagplatform.auth.dto.UserDto;
import com.featureflagplatform.auth.security.SecurityUser;
import com.featureflagplatform.auth.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "ADMIN-managed user accounts. Demo/assessment scope: an ADMIN creates an "
        + "account and a backend-generated password is returned once for them to copy and share out of band "
        + "— there is no self-service signup or email-based password reset. Accounts are disabled, never "
        + "deleted (a user with any flag/audit history can't be hard-deleted without violating a foreign "
        + "key — see V7__add_user_enabled_flag.sql).")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users", description = "ADMIN only. Server-side paginated. Never includes a "
            + "password or password hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of users"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated as VIEWER, not ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Page<UserDto>> list(@PageableDefault(size = 20, sort = "email") Pageable pageable) {
        return ResponseEntity.ok(userManagementService.list(pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a user with a backend-generated password", description = "ADMIN only. "
            + "Generates a cryptographically random 16-character password server-side (never client-supplied, "
            + "never Math.random()), stores only its bcrypt hash, and returns the plaintext password in this "
            + "one response — it is never retrievable again after this. Copy it now; the new user can change "
            + "it themselves afterward via PUT /api/v1/auth/me/password.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created — response includes the one-time generatedPassword"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or the email is already taken",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated as VIEWER, not ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<CreatedUserDto> create(@Valid @RequestBody CreateUserRequest request) {
        CreatedUserDto created = userManagementService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Disable a user account", description = "ADMIN only. A disabled account is rejected "
            + "at login and, since the account is re-checked from the database on every request rather than "
            + "trusted from the token, loses API access immediately — not just at its next login. An admin "
            + "cannot disable their own account (this would return 400), so there is always at least one "
            + "path back to re-enabling anyone.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User disabled"),
            @ApiResponse(responseCode = "400", description = "Attempted to disable your own account",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated as VIEWER, not ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No user with that ID",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<UserDto> disable(@PathVariable UUID id, @AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(userManagementService.disable(id, principal.user()));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Re-enable a disabled user account", description = "ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User enabled"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated as VIEWER, not ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No user with that ID",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<UserDto> enable(@PathVariable UUID id) {
        return ResponseEntity.ok(userManagementService.enable(id));
    }
}
