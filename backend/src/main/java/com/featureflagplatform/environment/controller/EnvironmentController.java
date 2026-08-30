package com.featureflagplatform.environment.controller;

import com.featureflagplatform.environment.dto.CreateEnvironmentRequest;
import com.featureflagplatform.environment.dto.EnvironmentDto;
import com.featureflagplatform.environment.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/environments")
@Tag(name = "Environments", description = "DEV/STAGING/PROD-style groupings that scope feature flags")
@SecurityRequirement(name = "bearerAuth")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @GetMapping
    @Operation(summary = "List all environments")
    @ApiResponse(responseCode = "200", description = "Environments returned")
    public ResponseEntity<List<EnvironmentDto>> listAll() {
        return ResponseEntity.ok(environmentService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one environment by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Environment found"),
            @ApiResponse(responseCode = "404", description = "No environment with that ID",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<EnvironmentDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(environmentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an environment", description = "ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Environment created"),
            @ApiResponse(responseCode = "400", description = "Validation failed or the name is already taken",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated as VIEWER, not ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<EnvironmentDto> create(@Valid @RequestBody CreateEnvironmentRequest request) {
        EnvironmentDto created = environmentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
