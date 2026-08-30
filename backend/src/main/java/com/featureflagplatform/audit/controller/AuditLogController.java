package com.featureflagplatform.audit.controller;

import com.featureflagplatform.audit.dto.AuditLogDto;
import com.featureflagplatform.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit Log", description = "Immutable history of every feature flag mutation")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "List audit entries", description = "Optionally filter by environment. Newest first, "
            + "server-side paginated. Available to both ADMIN and VIEWER — audit history contains no secrets, "
            + "only who changed what and when.")
    @ApiResponse(responseCode = "200", description = "Page of audit entries")
    public ResponseEntity<Page<AuditLogDto>> list(
            @Parameter(description = "Filter to a single environment") @RequestParam(required = false) UUID environmentId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLogDto> page = environmentId == null
                ? auditService.listAll(pageable)
                : auditService.listByEnvironment(environmentId, pageable);
        return ResponseEntity.ok(page);
    }
}
