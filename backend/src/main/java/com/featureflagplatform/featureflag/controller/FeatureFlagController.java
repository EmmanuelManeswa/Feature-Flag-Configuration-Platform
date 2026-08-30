package com.featureflagplatform.featureflag.controller;

import com.featureflagplatform.audit.dto.AuditLogDto;
import com.featureflagplatform.audit.service.AuditService;
import com.featureflagplatform.auth.security.SecurityUser;
import com.featureflagplatform.evaluation.dto.EvaluateRequest;
import com.featureflagplatform.evaluation.dto.EvaluationMetricsDto;
import com.featureflagplatform.evaluation.dto.EvaluationResultDto;
import com.featureflagplatform.evaluation.service.EvaluationService;
import com.featureflagplatform.featureflag.dto.CreateFeatureFlagRequest;
import com.featureflagplatform.featureflag.dto.FeatureFlagDto;
import com.featureflagplatform.featureflag.dto.UpdateFeatureFlagRequest;
import com.featureflagplatform.featureflag.event.FlagChangeNotifier;
import com.featureflagplatform.featureflag.service.FeatureFlagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/flags")
@Tag(name = "Feature Flags", description = "Create, update, evaluate, and inspect feature flags")
@SecurityRequirement(name = "bearerAuth")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final EvaluationService evaluationService;
    private final AuditService auditService;
    private final FlagChangeNotifier flagChangeNotifier;

    public FeatureFlagController(
            FeatureFlagService featureFlagService, EvaluationService evaluationService,
            AuditService auditService, FlagChangeNotifier flagChangeNotifier) {
        this.featureFlagService = featureFlagService;
        this.evaluationService = evaluationService;
        this.auditService = auditService;
        this.flagChangeNotifier = flagChangeNotifier;
    }

    @GetMapping
    @Operation(summary = "List feature flags", description = "Optionally filter by environment. Server-side paginated.")
    @ApiResponse(responseCode = "200", description = "Page of feature flags")
    public ResponseEntity<Page<FeatureFlagDto>> list(
            @Parameter(description = "Filter to a single environment") @RequestParam(required = false) UUID environmentId,
            @PageableDefault(size = 20, sort = "key") Pageable pageable) {
        return ResponseEntity.ok(featureFlagService.list(environmentId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one feature flag by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flag found"),
            @ApiResponse(responseCode = "404", description = "No flag with that ID",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<FeatureFlagDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(featureFlagService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a feature flag", description = "ADMIN only. Records an immutable CREATE audit entry.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Flag created"),
            @ApiResponse(responseCode = "400", description = "Validation failed, the environment doesn't exist, "
                    + "the key is already taken in that environment, or rolloutPercentage doesn't match the flag type",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated as VIEWER, not ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<FeatureFlagDto> create(
            @Valid @RequestBody CreateFeatureFlagRequest request, @AuthenticationPrincipal SecurityUser principal) {
        FeatureFlagDto created = featureFlagService.create(request, principal.user());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a feature flag", description = "ADMIN only. Requires the flag's current "
            + "`version` as `expectedVersion` — optimistic concurrency: if someone else changed the flag since "
            + "you loaded it, this returns 409 with the current version so you can refresh and retry. Records "
            + "an immutable UPDATE audit entry with the before/after values.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Flag updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated as VIEWER, not ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No flag with that ID",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "expectedVersion no longer matches — someone else "
                    + "modified this flag first. Response body includes the current version.",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<FeatureFlagDto> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateFeatureFlagRequest request,
            @AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.ok(featureFlagService.update(id, request, principal.user()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a feature flag", description = "ADMIN only. Records an immutable DELETE audit "
            + "entry (with the flag's last known value) before removing the row.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Flag deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated as VIEWER, not ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No flag with that ID",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal SecurityUser principal) {
        featureFlagService.delete(id, principal.user());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/evaluate")
    @Operation(summary = "Evaluate a flag for a given user context", description = "Cache-aside on Redis, keyed "
            + "by flag ID; falls back to Postgres on a cache miss or Redis failure. Deterministic: the same "
            + "stableIdentifier always resolves the same way for a given flag/version. Available to both ADMIN "
            + "and VIEWER — this is what the evaluation playground calls.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evaluation result"),
            @ApiResponse(responseCode = "400", description = "Validation failed (e.g. missing stableIdentifier)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "No flag with that ID",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<EvaluationResultDto> evaluate(@PathVariable UUID id, @Valid @RequestBody EvaluateRequest request) {
        return ResponseEntity.ok(evaluationService.evaluate(id, request.toDomain()));
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "Audit history for one flag", description = "Every CREATE/UPDATE/DELETE recorded for "
            + "this flag ID, newest first, with before/after values.")
    @ApiResponse(responseCode = "200", description = "Page of audit entries")
    public ResponseEntity<Page<AuditLogDto>> auditForFlag(
            @PathVariable UUID id, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(auditService.listByEntity(id, pageable));
    }

    @GetMapping("/{id}/metrics")
    @Operation(summary = "Evaluation metrics for one flag", description = "Basic in-process evaluation counters: "
            + "how many times this flag has been evaluated, broken down by result. Backed by the same Micrometer "
            + "counters exposed in aggregate (all flags, Prometheus format) at /actuator/prometheus — this endpoint "
            + "just scopes and shapes them for one flag. Counts reset when the backend process restarts; this is "
            + "an in-memory operational signal, not a durable analytics store.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evaluation counts for this flag"),
            @ApiResponse(responseCode = "404", description = "No flag with that ID",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<EvaluationMetricsDto> metrics(@PathVariable UUID id) {
        FeatureFlagDto flag = featureFlagService.getById(id);
        return ResponseEntity.ok(evaluationService.getMetrics(flag.key()));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Live flag-change stream (Server-Sent Events)", description = "Emits a `flag-change` "
            + "event (flag ID/key, environment, CREATED/UPDATED/DELETED, timestamp) whenever any flag is created, "
            + "updated, or deleted, so a connected client can invalidate its cache instead of polling. Also emits "
            + "a `connected` event immediately on subscribe and a keep-alive comment every 15s. "
            + "**Not meaningfully testable from Swagger UI's \"Try it out\"** (it renders a single response, not a "
            + "stream) — use `curl -N` with a bearer token, or the app's own live-updating flags list, to see it "
            + "in action. Available to both ADMIN and VIEWER.")
    @ApiResponse(responseCode = "200", description = "text/event-stream connection opened; stays open until the "
            + "client disconnects or the 30-minute server-side timeout is reached (the frontend reconnects "
            + "automatically either way).")
    public SseEmitter stream() {
        return flagChangeNotifier.subscribe();
    }
}
