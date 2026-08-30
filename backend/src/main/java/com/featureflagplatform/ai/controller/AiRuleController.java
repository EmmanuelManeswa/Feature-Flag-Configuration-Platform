package com.featureflagplatform.ai.controller;

import com.featureflagplatform.ai.dto.RuleAssistantRequest;
import com.featureflagplatform.ai.dto.RuleProposalDto;
import com.featureflagplatform.ai.service.AiRuleAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Rule Assistant", description = "Natural language -> structured targeting rule PROPOSAL only. "
        + "Never persists anything — apply a proposal by submitting it through the normal flag create/update "
        + "endpoints, which validate it exactly as they would a human-authored rule.")
@SecurityRequirement(name = "bearerAuth")
public class AiRuleController {

    private final AiRuleAssistantService aiRuleAssistantService;

    public AiRuleController(AiRuleAssistantService aiRuleAssistantService) {
        this.aiRuleAssistantService = aiRuleAssistantService;
    }

    @PostMapping("/rule-proposals")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Generate a targeting rule proposal from a natural-language description",
            description = "ADMIN only. Example input: \"enable this for 20% of users in Harare except "
                    + "internal staff\". The response is a proposal for review — it is never saved. Every "
                    + "field is schema- and domain-validated before it's returned; if the AI provider fails "
                    + "or returns something that doesn't validate, this returns 503 with a generic message "
                    + "(the specific reason is logged server-side with the correlation ID, never exposed to "
                    + "the client).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Structured proposal (strategy, rolloutPercentage, "
                    + "rules, explanation) — review before applying"),
            @ApiResponse(responseCode = "400", description = "Validation failed (e.g. empty request)",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated as VIEWER, not ADMIN",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "AI unavailable (provider down, timed out, or "
                    + "returned something that failed validation) — configure the rule manually instead",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<RuleProposalDto> generateProposal(@Valid @RequestBody RuleAssistantRequest request) {
        return ResponseEntity.ok(aiRuleAssistantService.generateProposal(request));
    }
}
